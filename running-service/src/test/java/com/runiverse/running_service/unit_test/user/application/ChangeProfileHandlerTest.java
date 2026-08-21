package com.runiverse.running_service.unit_test.user.application;

import com.github.f4b6a3.uuid.UuidCreator;
import com.runiverse.running_service.application.user.command.profile.ChangeProfileCommand;
import com.runiverse.running_service.application.user.command.profile.ChangeProfileHandler;
import com.runiverse.running_service.application.user.command.profile.ChangeProfileResult;
import com.runiverse.running_service.application.user.exception.OnboardingNotCompletedException;
import com.runiverse.running_service.application.user.exception.UserNotFoundException;
import com.runiverse.running_service.application.user.port.out.LoadOnboardingPort;
import com.runiverse.running_service.application.user.port.out.LoadUserByIdPort;
import com.runiverse.running_service.application.user.port.out.UpdateIntroductionPort;
import com.runiverse.running_service.application.user.port.out.UpdateOnboardingPort;
import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.domain.user.aggregate.User;
import com.runiverse.running_service.domain.user.aggregate.UserOnboarding;
import com.runiverse.running_service.domain.user.vo.Introduction;
import com.runiverse.running_service.domain.user.vo.ProfileVisibility;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("프로필 수정 단위 테스트")
public class ChangeProfileHandlerTest {

    // PasswordHash VO가 Argon2id 형식만 허용하므로 형식에 맞는 값을 쓴다
    private static final String PASSWORD_HASH =
            "$argon2id$v=19$m=16384,t=2,p=1$c29tZXNhbHQ$aGFzaHZhbHVl";
    private static final String NICKNAME = "러너킴";
    private static final String INTRODUCTION = "즐겁게 달려요";
    private static final LocalDate BIRTHDAY = LocalDate.of(1998, 12, 16);
    private static final BigDecimal WEIGHT = new BigDecimal("70.5");
    private static final BigDecimal HEIGHT = new BigDecimal("175.0");

    @Mock
    private LoadUserByIdPort loadUserByIdPort;

    @Mock
    private UpdateIntroductionPort updateIntroductionPort;

    @Mock
    private LoadOnboardingPort loadOnboardingPort;

    @Mock
    private UpdateOnboardingPort updateOnboardingPort;

    @InjectMocks
    private ChangeProfileHandler handler;

    private static User userOf(UUID userId) {
        return new User(userId, "runner@runiverse.com", PASSWORD_HASH, true,
                null, ProfileVisibility.PUBLIC, "");
    }

    private static UserOnboarding onboardingOf(UUID userId) {
        return new UserOnboarding(new UserId(userId), NICKNAME, "MALE", BIRTHDAY,
                330, WEIGHT, HEIGHT);
    }

    private void givenUser(UUID userId) {
        when(loadUserByIdPort.loadById(new UserId(userId))).thenReturn(Optional.of(userOf(userId)));
    }

    private void givenOnboarded(UUID userId) {
        when(loadOnboardingPort.loadOnboarding(new UserId(userId)))
                .thenReturn(Optional.of(onboardingOf(userId)));
    }

    @Test
    @DisplayName("소개글만 보내면 온보딩은 건드리지 않는다")
    void changesIntroductionOnly() {
        // given
        UUID userId = UuidCreator.getTimeOrderedEpoch();
        givenUser(userId);

        // when
        ChangeProfileResult result = handler.handle(
                new ChangeProfileCommand(userId, INTRODUCTION, null, null, null, null));

        // then -> 소개글은 users에 있어 온보딩 조회 자체가 필요 없다
        assertThat(result.introduction()).isEqualTo(INTRODUCTION);
        assertThat(result.weightKg()).isNull();
        verify(updateIntroductionPort)
                .updateIntroduction(new UserId(userId), new Introduction(INTRODUCTION));
        verifyNoInteractions(loadOnboardingPort, updateOnboardingPort);
    }

    @Test
    @DisplayName("빈 문자열을 보내면 소개글을 지운다")
    void clearsIntroductionWithEmptyString() {
        // given -> 편집 화면에서 소개글을 비우고 저장한 경우다
        UUID userId = UuidCreator.getTimeOrderedEpoch();
        givenUser(userId);

        // when
        ChangeProfileResult result = handler.handle(
                new ChangeProfileCommand(userId, "", null, null, null, null));

        // then -> 지우는 것도 값을 바꾸는 것이라 갱신이 일어나야 한다
        assertThat(result.introduction()).isEmpty();
        verify(updateIntroductionPort).updateIntroduction(new UserId(userId), new Introduction(""));
    }

    @Test
    @DisplayName("보낸 온보딩 값만 바꾸고 나머지는 그대로 둔다")
    void changesOnlyGivenOnboardingFields() {
        // given
        UUID userId = UuidCreator.getTimeOrderedEpoch();
        givenUser(userId);
        givenOnboarded(userId);
        BigDecimal newWeight = new BigDecimal("68.0");

        // when
        handler.handle(new ChangeProfileCommand(userId, null, null, null, newWeight, null));

        // then -> 안 보낸 성별·생일·키는 기존 값이 유지된 채 저장된다
        ArgumentCaptor<UserOnboarding> captor = ArgumentCaptor.forClass(UserOnboarding.class);
        verify(updateOnboardingPort).updateOnboarding(captor.capture());
        UserOnboarding saved = captor.getValue();
        assertThat(saved.getWeight().value()).isEqualByComparingTo(newWeight);
        assertThat(saved.getHeight().value()).isEqualByComparingTo(HEIGHT);
        assertThat(saved.getBirthday().value()).isEqualTo(BIRTHDAY);
        assertThat(saved.getNickname().value()).isEqualTo(NICKNAME);
    }

    @Test
    @DisplayName("응답에는 보낸 필드만 담고 나머지는 null로 둔다")
    void returnsOnlyRequestedFields() {
        // given -> 안 보낸 값도 갱신본에는 들어 있어 그대로 꺼내면 함께 나간다
        UUID userId = UuidCreator.getTimeOrderedEpoch();
        givenUser(userId);
        givenOnboarded(userId);
        BigDecimal newWeight = new BigDecimal("68.0");

        // when
        ChangeProfileResult result = handler.handle(
                new ChangeProfileCommand(userId, null, null, null, newWeight, null));

        // then
        assertThat(result.weightKg()).isEqualByComparingTo(newWeight);
        assertThat(result.introduction()).isNull();
        assertThat(result.gender()).isNull();
        assertThat(result.birthday()).isNull();
        assertThat(result.heightCm()).isNull();
    }

    @Test
    @DisplayName("온보딩 전에 온보딩 값을 보내면 소개글도 바꾸지 않고 막는다")
    void rejectsOnboardingFieldsBeforeOnboarding() {
        // given -> 소개글만 반영하고 나머지를 무시하면 절반만 저장된 것을 클라이언트가 모른다
        UUID userId = UuidCreator.getTimeOrderedEpoch();
        givenUser(userId);
        when(loadOnboardingPort.loadOnboarding(new UserId(userId))).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> handler.handle(
                new ChangeProfileCommand(userId, INTRODUCTION, null, null, WEIGHT, null)))
                .isInstanceOf(OnboardingNotCompletedException.class);
        verifyNoInteractions(updateIntroductionPort, updateOnboardingPort);
    }

    @Test
    @DisplayName("온보딩 전이어도 소개글만 보내면 바꿀 수 있다")
    void allowsIntroductionBeforeOnboarding() {
        // given
        UUID userId = UuidCreator.getTimeOrderedEpoch();
        givenUser(userId);

        // when
        ChangeProfileResult result = handler.handle(
                new ChangeProfileCommand(userId, INTRODUCTION, null, null, null, null));

        // then -> 소개글은 users에 있어 온보딩과 무관하다
        assertThat(result.introduction()).isEqualTo(INTRODUCTION);
        verifyNoInteractions(loadOnboardingPort);
    }

    @Test
    @DisplayName("사용자가 없으면 아무것도 바꾸지 않고 예외를 던진다")
    void throwsWhenUserNotFound() {
        // given
        UUID unknownUserId = UuidCreator.getTimeOrderedEpoch();
        when(loadUserByIdPort.loadById(new UserId(unknownUserId))).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> handler.handle(
                new ChangeProfileCommand(unknownUserId, INTRODUCTION, null, null, null, null)))
                .isInstanceOf(UserNotFoundException.class);
        verifyNoInteractions(updateIntroductionPort, loadOnboardingPort, updateOnboardingPort);
    }
}
