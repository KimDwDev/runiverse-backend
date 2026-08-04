package com.runiverse.running_service.integration_test.user;

import com.runiverse.running_service.application.auth.command.login.LoginCommand;
import com.runiverse.running_service.application.auth.command.login.LoginHandler;
import com.runiverse.running_service.application.auth.command.signup.SignUpCommand;
import com.runiverse.running_service.application.auth.command.signup.SignUpHandler;
import com.runiverse.running_service.application.user.command.onboard.CompleteOnboardCommand;
import com.runiverse.running_service.application.user.command.onboard.CompleteOnboardHandler;
import com.runiverse.running_service.application.user.command.onboard.CompleteOnboardResult;
import com.runiverse.running_service.application.user.exception.AlreadyOnboardException;
import com.runiverse.running_service.application.user.exception.NicknameAlreadyExistsException;
import com.runiverse.running_service.application.user.exception.UserNotFoundException;
import com.runiverse.running_service.domain.user.aggregate.UserOnboard;
import com.runiverse.running_service.domain.user.exception.InvalidNicknameLengthException;
import com.runiverse.running_service.domain.user.vo.Gender;
import com.runiverse.running_service.integration_test.IntegrationTestSupport;
import com.github.f4b6a3.uuid.UuidCreator;
import static org.assertj.core.api.Assertions.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@DisplayName("온보딩 완료 통합 테스트")
public class CompleteOnboardIntegrationTest extends IntegrationTestSupport {
    private static final String EMAIL = "runner@runiverse.com";
    private static final String PASSWORD = "Password123!";
    private static final String NICKNAME = "러너킴";
    private static final String GENDER = "MALE";
    private static final LocalDate BIRTHDAY = LocalDate.of(1999, 5, 20);
    private static final int AVG_PACE = 330;
    private static final BigDecimal WEIGHT = new BigDecimal("70.5");
    private static final BigDecimal HEIGHT = new BigDecimal("175.0");
    private SignUpHandler signUpHandler;
    private LoginHandler loginHandler;
    private CompleteOnboardHandler completeOnboardHandler;
    @BeforeEach
    void setUp() {
        signUpHandler = new SignUpHandler(
                userStore, passwordHasher, userIdGenerator, userStore);
        loginHandler = new LoginHandler(
                userStore, passwordHasher, tokenProvider,
                tokenProvider, refreshTokenStore, onboardStore);
        completeOnboardHandler = new CompleteOnboardHandler(
                userStore,     // LoadUserByIdPort
                onboardStore,  // ExistsOnboardPort
                onboardStore,  // CheckNicknameDuplicatePort
                onboardStore   // SaveOnboardPort
        );
    }
    private UUID signUp(String email) {
        return signUpHandler.handle(new SignUpCommand(email, PASSWORD)).userId();
    }
    private CompleteOnboardCommand command(UUID userId, String nickname) {
        return new CompleteOnboardCommand(
                userId, nickname, GENDER, BIRTHDAY, AVG_PACE, WEIGHT, HEIGHT);
    }

    @Test
    @DisplayName("온보딩을 완료하면 입력한 값이 VO로 저장된다")
    void completeOnboardSuccess() {
        // given
        UUID userId = signUp(EMAIL);
        // when
        CompleteOnboardResult result = completeOnboardHandler.handle(command(userId, NICKNAME));
        // then
        assertThat(result.userId()).isEqualTo(userId);
        assertThat(result.nickname()).isEqualTo(NICKNAME);

        UserOnboard saved = onboardStore.findByUserId(userId).orElseThrow();
        assertThat(saved.getNickname().value()).isEqualTo(NICKNAME);
        assertThat(saved.getGender()).isEqualTo(Gender.MALE);
        assertThat(saved.getBirthday().value()).isEqualTo(BIRTHDAY);
        assertThat(saved.getAvgPace().secondPerKm()).isEqualTo(AVG_PACE);
        assertThat(saved.getWeight().value()).isEqualByComparingTo(WEIGHT);
        assertThat(saved.getHeight().value()).isEqualByComparingTo(HEIGHT);
    }
    @Test
    @DisplayName("온보딩을 마치면 이후 로그인에서 isOnboarded가 true가 된다")
    void onboardIsReflectedInLogin() {
        // given
        UUID userId = signUp(EMAIL);
        assertThat(loginHandler.handle(new LoginCommand(EMAIL, PASSWORD)).isOnboarded()).isFalse();
        // when
        completeOnboardHandler.handle(command(userId, NICKNAME));
        // then
        assertThat(loginHandler.handle(new LoginCommand(EMAIL, PASSWORD)).isOnboarded()).isTrue();
    }
    @Test
    @DisplayName("유저가 온보딩 정보를 갖게 된다")
    void userAggregateHoldsOnboard() {
        // given
        UUID userId = signUp(EMAIL);
        // when
        completeOnboardHandler.handle(command(userId, NICKNAME));
        // then
        assertThat(userStore.findById(userId).orElseThrow().hasOnboarded()).isTrue();
    }
    @Test
    @DisplayName("가입하지 않은 userId면 UserNotFoundException이 발생한다")
    void onboardWithUnknownUser() {
        // when & then
        assertThatThrownBy(() -> completeOnboardHandler.handle(
                command(UuidCreator.getTimeOrderedEpoch(), NICKNAME)))
                .isInstanceOf(UserNotFoundException.class);

        assertThat(onboardStore.size()).isZero();
    }
    @Test
    @DisplayName("이미 온보딩한 유저가 다시 시도하면 AlreadyOnboardException이 발생한다")
    void onboardTwice() {
        // given
        UUID userId = signUp(EMAIL);
        completeOnboardHandler.handle(command(userId, NICKNAME));
        // when & then
        assertThatThrownBy(() -> completeOnboardHandler.handle(command(userId, "새러너")))
                .isInstanceOf(AlreadyOnboardException.class);
        // 기존 닉네임이 덮어써지지 않는다
        assertThat(onboardStore.findByUserId(userId).orElseThrow().getNickname().value())
                .isEqualTo(NICKNAME);
    }
    @Test
    @DisplayName("다른 유저가 쓰고 있는 닉네임이면 NicknameAlreadyExistsException이 발생한다")
    void onboardWithDuplicateNickname() {
        // given
        UUID first = signUp(EMAIL);
        UUID second = signUp("other@runiverse.com");
        completeOnboardHandler.handle(command(first, NICKNAME));
        // when & then
        assertThatThrownBy(() -> completeOnboardHandler.handle(command(second, NICKNAME)))
                .isInstanceOf(NicknameAlreadyExistsException.class);

        assertThat(onboardStore.size()).isEqualTo(1);
        assertThat(userStore.findById(second).orElseThrow().hasOnboarded()).isFalse();
    }
    @Test
    @DisplayName("닉네임 앞뒤 공백은 제거된 뒤 중복 검사에 걸린다")
    void nicknameIsNormalizedBeforeDuplicateCheck() {
        // given
        UUID first = signUp(EMAIL);
        UUID second = signUp("other@runiverse.com");
        completeOnboardHandler.handle(command(first, NICKNAME));
        // when & then
        assertThatThrownBy(() -> completeOnboardHandler.handle(command(second, "  " + NICKNAME + "  ")))
                .isInstanceOf(NicknameAlreadyExistsException.class);
    }
    @Test
    @DisplayName("닉네임이 규칙에 어긋나면 도메인 예외가 전파되고 아무것도 저장되지 않는다")
    void onboardWithInvalidNickname() {
        // given
        UUID userId = signUp(EMAIL);
        // when & then - 2자 미만
        assertThatThrownBy(() -> completeOnboardHandler.handle(command(userId, "킴")))
                .isInstanceOf(InvalidNicknameLengthException.class);

        assertThat(onboardStore.size()).isZero();
        assertThat(userStore.findById(userId).orElseThrow().hasOnboarded()).isFalse();
    }
    @Test
    @DisplayName("실패한 뒤 올바른 값으로 다시 시도하면 온보딩할 수 있다")
    void retryAfterValidationFailure() {
        // given
        UUID userId = signUp(EMAIL);
        assertThatThrownBy(() -> completeOnboardHandler.handle(command(userId, "킴")))
                .isInstanceOf(InvalidNicknameLengthException.class);
        // when
        CompleteOnboardResult result = completeOnboardHandler.handle(command(userId, NICKNAME));
        // then
        assertThat(result.nickname()).isEqualTo(NICKNAME);
        assertThat(onboardStore.size()).isEqualTo(1);
    }
}
