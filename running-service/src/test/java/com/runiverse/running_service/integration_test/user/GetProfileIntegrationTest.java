package com.runiverse.running_service.integration_test.user;

import com.github.f4b6a3.uuid.UuidCreator;
import com.runiverse.running_service.application.auth.command.signup.SignUpCommand;
import com.runiverse.running_service.application.auth.command.signup.SignUpHandler;
import com.runiverse.running_service.application.user.command.nickname.ChangeNicknameCommand;
import com.runiverse.running_service.application.user.command.nickname.ChangeNicknameHandler;
import com.runiverse.running_service.application.user.command.onboarding.CompleteOnboardingCommand;
import com.runiverse.running_service.application.user.command.onboarding.CompleteOnboardingHandler;
import com.runiverse.running_service.application.user.exception.UserNotFoundException;
import com.runiverse.running_service.application.user.query.profile.GetProfileHandler;
import com.runiverse.running_service.application.user.query.profile.GetProfileQuery;
import com.runiverse.running_service.application.user.query.profile.GetProfileResult;
import com.runiverse.running_service.integration_test.IntegrationTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("내 기본 정보 조회 통합 테스트")
public class GetProfileIntegrationTest extends IntegrationTestSupport {

    private static final String EMAIL = "runner@runiverse.com";
    private static final String OTHER_EMAIL = "other@runiverse.com";
    private static final String PASSWORD = "Password123!";
    private static final String NICKNAME = "러너킴";
    private static final String NEW_NICKNAME = "동완러너";

    private SignUpHandler signUpHandler;
    private CompleteOnboardingHandler completeOnboardingHandler;
    private ChangeNicknameHandler changeNicknameHandler;
    private GetProfileHandler getProfileHandler;

    @BeforeEach
    void setUp() {
        signUpHandler = newSignUpHandler();
        completeOnboardingHandler = new CompleteOnboardingHandler(
                userStore,        // LoadUserByIdPort
                onboardingStore,  // ExistsOnboardingPort
                onboardingStore,  // CheckNicknameDuplicatePort
                onboardingStore   // SaveOnboardingPort
        );
        changeNicknameHandler = new ChangeNicknameHandler(
                onboardingStore,  // LoadNicknamePort
                onboardingStore,  // CheckNicknameDuplicatePort
                onboardingStore   // UpdateNicknamePort
        );
        getProfileHandler = new GetProfileHandler(
                userStore,       // LoadUserByIdPort
                onboardingStore  // LoadNicknamePort
        );
    }

    private UUID signUp(String email) {
        return signUpHandler.handle(new SignUpCommand(issueVerificationTicket(email), PASSWORD)).userId();
    }

    private void completeOnboarding(UUID userId, String nickname) {
        completeOnboardingHandler.handle(new CompleteOnboardingCommand(
                userId, nickname, "MALE", LocalDate.of(1999, 5, 20),
                330, new BigDecimal("70.5"), new BigDecimal("175.0")));
    }

    private GetProfileResult profileOf(UUID userId) {
        return getProfileHandler.handle(new GetProfileQuery(userId));
    }

    @Test
    @DisplayName("가입만 마친 사용자는 온보딩 미완료로 답한다")
    void reportsNotOnboardedRightAfterSignUp() {
        // given
        UUID userId = signUp(EMAIL);

        // when
        GetProfileResult result = profileOf(userId);

        // then -> 앱은 이 값을 보고 홈이 아니라 온보딩 화면으로 보낸다
        assertThat(result.userId()).isEqualTo(userId);
        assertThat(result.isOnboarded()).isFalse();
        assertThat(result.nickname()).isNull();
    }

    @Test
    @DisplayName("온보딩을 마치면 같은 사용자가 완료로 바뀌고 닉네임이 따라온다")
    void reportsOnboardedAfterOnboarding() {
        // given
        UUID userId = signUp(EMAIL);
        assertThat(profileOf(userId).isOnboarded()).isFalse();

        // when
        completeOnboarding(userId, NICKNAME);

        // then -> 온보딩 완료 판정의 유일한 경로가 이 API다
        GetProfileResult result = profileOf(userId);
        assertThat(result.isOnboarded()).isTrue();
        assertThat(result.nickname()).isEqualTo(NICKNAME);
    }

    @Test
    @DisplayName("닉네임을 바꾸면 바뀐 닉네임으로 답한다")
    void reflectsChangedNickname() {
        // given -> 닉네임 변경은 user_onboardings의 컬럼만 갱신한다
        UUID userId = signUp(EMAIL);
        completeOnboarding(userId, NICKNAME);

        // when
        changeNicknameHandler.handle(new ChangeNicknameCommand(userId, NEW_NICKNAME));

        // then -> 온보딩 시점 스냅샷이 아니라 현재 닉네임을 읽어야 한다
        assertThat(profileOf(userId).nickname()).isEqualTo(NEW_NICKNAME);
    }

    @Test
    @DisplayName("다른 사용자의 온보딩은 내 판정에 영향을 주지 않는다")
    void otherUsersOnboardingDoesNotLeak() {
        // given
        UUID userId = signUp(EMAIL);
        UUID otherUserId = signUp(OTHER_EMAIL);
        completeOnboarding(otherUserId, NICKNAME);

        // when & then
        assertThat(profileOf(userId).isOnboarded()).isFalse();
        assertThat(profileOf(otherUserId).isOnboarded()).isTrue();
    }

    @Test
    @DisplayName("가입한 적 없는 사용자는 조회할 수 없다")
    void throwsForUnknownUser() {
        // when & then -> 토큰은 유효하지만 계정이 없는 경우다
        assertThatThrownBy(() -> profileOf(UuidCreator.getTimeOrderedEpoch()))
                .isInstanceOf(UserNotFoundException.class);
    }
}
