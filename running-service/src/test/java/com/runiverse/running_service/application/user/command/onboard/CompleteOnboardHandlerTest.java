package com.runiverse.running_service.application.user.command.onboard;

import com.github.f4b6a3.uuid.UuidCreator;
import com.runiverse.running_service.application.user.exception.AlreadyOnboardException;
import com.runiverse.running_service.application.user.exception.NicknameAlreadyExistsException;
import com.runiverse.running_service.application.user.exception.UserNotFoundException;
import com.runiverse.running_service.application.user.port.out.CheckNicknameDuplicatePort;
import com.runiverse.running_service.application.user.port.out.ExistsOnboardPort;
import com.runiverse.running_service.application.user.port.out.LoadUserByIdPort;
import com.runiverse.running_service.application.user.port.out.SaveOnboardPort;
import com.runiverse.running_service.domain.user.aggregate.User;
import com.runiverse.running_service.domain.user.aggregate.UserOnboard;
import com.runiverse.running_service.domain.user.exception.InvalidUserIdFormatException;
import com.runiverse.running_service.domain.user.vo.Gender;
import com.runiverse.running_service.domain.user.vo.Nickname;
import com.runiverse.running_service.domain.user.vo.UserId;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class CompleteOnboardHandlerTest {

    // PasswordHash VO가 Argon2id 형식만 허용하므로 형식에 맞는 값을 쓴다
    private static final String PASSWORD_HASH =
            "$argon2id$v=19$m=16384,t=2,p=1$c29tZXNhbHQ$aGFzaHZhbHVl";

    private static final UUID USER_ID = UuidCreator.getTimeOrderedEpoch();
    private static final String EMAIL = "test@example.com";
    private static final String NICKNAME = "러너킴";
    private static final String GENDER = "MALE";
    private static final LocalDate BIRTHDAY = LocalDate.of(1999, 5, 20);
    private static final int AVG_PACE = 330;
    private static final BigDecimal WEIGHT = new BigDecimal("70.5");
    private static final BigDecimal HEIGHT = new BigDecimal("175.0");

    @Mock
    private LoadUserByIdPort loadUserByIdPort;

    @Mock
    private ExistsOnboardPort existsOnboardPort;

    @Mock
    private CheckNicknameDuplicatePort checkNicknameDuplicatePort;

    @Mock
    private SaveOnboardPort saveOnboardPort;

    @InjectMocks
    private CompleteOnboardHandler completeOnboardHandler;

    private CompleteOnboardCommand command(String nickname) {
        return new CompleteOnboardCommand(
                USER_ID, nickname, GENDER, BIRTHDAY, AVG_PACE, WEIGHT, HEIGHT
        );
    }

    @Test
    @DisplayName("온보딩을 완료하면 user_onboard 행을 저장하고 결과를 반환한다")
    void completeOnboardSuccess() {
        // given
        when(loadUserByIdPort.loadById(new UserId(USER_ID)))
                .thenReturn(Optional.of(new User(USER_ID, EMAIL, PASSWORD_HASH)));
        when(existsOnboardPort.existsByUserId(new UserId(USER_ID))).thenReturn(false);
        when(checkNicknameDuplicatePort.existsByNickname(any(Nickname.class))).thenReturn(false);

        // when
        CompleteOnboardResult result = completeOnboardHandler.handle(command(NICKNAME));

        // then
        assertThat(result.userId()).isEqualTo(USER_ID);
        assertThat(result.nickname()).isEqualTo(NICKNAME);

        ArgumentCaptor<UserOnboard> captor = ArgumentCaptor.forClass(UserOnboard.class);
        verify(saveOnboardPort).saveOnboard(captor.capture());

        UserOnboard saved = captor.getValue();
        assertThat(saved.getUserId().value()).isEqualTo(USER_ID);
        assertThat(saved.getNickname().value()).isEqualTo(NICKNAME);
        assertThat(saved.getGender()).isEqualTo(Gender.MALE);
        assertThat(saved.getBirthday().value()).isEqualTo(BIRTHDAY);
        assertThat(saved.getAvgPace().secondPerKm()).isEqualTo(AVG_PACE);
        assertThat(saved.getWeight().value()).isEqualByComparingTo(WEIGHT);
        assertThat(saved.getHeight().value()).isEqualByComparingTo(HEIGHT);
    }

    @Test
    @DisplayName("유저가 없으면 예외가 발생하고 아무것도 저장하지 않는다")
    void completeOnboardWithUnknownUserFails() {
        // given
        when(loadUserByIdPort.loadById(new UserId(USER_ID))).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> completeOnboardHandler.handle(command(NICKNAME)))
                .isInstanceOf(UserNotFoundException.class);

        verify(saveOnboardPort, never()).saveOnboard(any());
    }

    @Test
    @DisplayName("이미 온보딩한 유저는 다시 완료할 수 없다")
    void completeOnboardTwiceFails() {
        // given
        when(loadUserByIdPort.loadById(new UserId(USER_ID)))
                .thenReturn(Optional.of(new User(USER_ID, EMAIL, PASSWORD_HASH)));
        when(existsOnboardPort.existsByUserId(new UserId(USER_ID))).thenReturn(true);

        // when & then -> 통과시키면 PK 중복으로 커밋 시점에 500이 난다
        assertThatThrownBy(() -> completeOnboardHandler.handle(command(NICKNAME)))
                .isInstanceOf(AlreadyOnboardException.class)
                .hasMessage("이미 온보딩을 완료했습니다.");

        // 닉네임 검사까지 가지 않는다 -> 엉뚱한 안내를 주지 않기 위해서다
        verify(checkNicknameDuplicatePort, never()).existsByNickname(any());
        verify(saveOnboardPort, never()).saveOnboard(any());
    }

    @Test
    @DisplayName("닉네임이 이미 쓰이고 있으면 저장하지 않는다")
    void completeOnboardWithDuplicateNicknameFails() {
        // given
        when(loadUserByIdPort.loadById(new UserId(USER_ID)))
                .thenReturn(Optional.of(new User(USER_ID, EMAIL, PASSWORD_HASH)));
        when(existsOnboardPort.existsByUserId(new UserId(USER_ID))).thenReturn(false);
        when(checkNicknameDuplicatePort.existsByNickname(any(Nickname.class))).thenReturn(true);

        // when & then
        assertThatThrownBy(() -> completeOnboardHandler.handle(command(NICKNAME)))
                .isInstanceOf(NicknameAlreadyExistsException.class)
                .hasMessage("이미 사용 중인 닉네임입니다.");

        verify(saveOnboardPort, never()).saveOnboard(any());
    }

    @Test
    @DisplayName("닉네임은 정규화된 값으로 중복 검사하고 저장한다")
    void completeOnboardNormalizesNickname() {
        // given -> 원본으로 검사하면 " 러너킴 "이 통과한 뒤 DB 유니크에서 터진다
        when(loadUserByIdPort.loadById(new UserId(USER_ID)))
                .thenReturn(Optional.of(new User(USER_ID, EMAIL, PASSWORD_HASH)));
        when(existsOnboardPort.existsByUserId(new UserId(USER_ID))).thenReturn(false);
        when(checkNicknameDuplicatePort.existsByNickname(any(Nickname.class))).thenReturn(false);

        // when
        CompleteOnboardResult result = completeOnboardHandler.handle(command("  러너킴  "));

        // then
        verify(checkNicknameDuplicatePort).existsByNickname(new Nickname(NICKNAME));
        assertThat(result.nickname()).isEqualTo(NICKNAME);
    }

    @Test
    @DisplayName("UUIDv7이 아닌 userId는 조회 전에 걸러진다")
    void completeOnboardWithInvalidUserIdFails() {
        // given
        UUID uuidV4 = UUID.randomUUID();

        // when & then -> 위조된 ID가 DB까지 가지 않는다
        assertThatThrownBy(() -> completeOnboardHandler.handle(new CompleteOnboardCommand(
                uuidV4, NICKNAME, GENDER, BIRTHDAY, AVG_PACE, WEIGHT, HEIGHT
        ))).isInstanceOf(InvalidUserIdFormatException.class);

        verify(loadUserByIdPort, never()).loadById(any());
    }
}
