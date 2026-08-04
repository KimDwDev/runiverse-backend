package com.runiverse.running_service.integration_test.auth;

import com.runiverse.running_service.application.auth.command.signup.SignUpCommand;
import com.runiverse.running_service.application.auth.command.signup.SignUpHandler;
import com.runiverse.running_service.application.auth.command.signup.SignUpResult;
import com.runiverse.running_service.application.auth.exception.EmailAlreadyExistsException;
import com.runiverse.running_service.domain.user.aggregate.User;
import com.runiverse.running_service.domain.user.exception.InvalidEmailFormatException;
import com.runiverse.running_service.integration_test.IntegrationTestSupport;
import com.runiverse.running_service.integration_test.fake.FakePasswordHasher;
import com.runiverse.running_service.integration_test.fake.FakeUserIdGenerator;
import com.runiverse.running_service.integration_test.fake.InMemoryUserStore;
import static org.assertj.core.api.Assertions.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("회원가입 통합 테스트")
public class SignUpIntegrationTest extends IntegrationTestSupport {
    private static final String EMAIL = "runner@runiverse.com";
    private static final String PASSWORD = "Password123!";
    private SignUpHandler signUpHandler;
    @BeforeEach
    void setUp() {
        // Fake는 상태를 가지므로 테스트마다 새로 만든다
        userStore = new InMemoryUserStore();
        passwordHasher = new FakePasswordHasher();
        signUpHandler = new SignUpHandler(
                userStore,
                passwordHasher,
                new FakeUserIdGenerator(),
                userStore
        );
    }
    @Test
    @DisplayName("회원가입에 성공하면 UUIDv7 userId를 반환하고 유저가 저장된다")
    void signUpSuccess() {
        // when
        SignUpResult result = signUpHandler.handle(new SignUpCommand(EMAIL, PASSWORD));
        // then
        assertThat(result.userId()).isNotNull();
        assertThat(result.userId().version()).isEqualTo(7);
        assertThat(userStore.size()).isEqualTo(1);
        User saved = userStore.findById(result.userId()).orElseThrow();
        assertThat(saved.getEmail().value()).isEqualTo(EMAIL);
    }
    @Test
    @DisplayName("비밀번호는 평문이 아니라 해시로 저장된다")
    void signUpStoresHashedPassword() {
        // when
        SignUpResult result = signUpHandler.handle(new SignUpCommand(EMAIL, PASSWORD));
        // then
        User saved = userStore.findById(result.userId()).orElseThrow();
        String storedHash = saved.getPasswordHash().value();
        assertThat(storedHash).isNotEqualTo(PASSWORD);
        assertThat(passwordHasher.matches(PASSWORD, storedHash)).isTrue();
        assertThat(passwordHasher.matches("WrongPassword1!", storedHash)).isFalse();
    }
    @Test
    @DisplayName("이미 가입된 이메일이면 EmailAlreadyExistsException이 발생한다")
    void signUpWithDuplicateEmail() {
        // given
        signUpHandler.handle(new SignUpCommand(EMAIL, PASSWORD));
        // when & then
        assertThatThrownBy(() -> signUpHandler.handle(new SignUpCommand(EMAIL, "Another123!")))
                .isInstanceOf(EmailAlreadyExistsException.class);
        // 중복 요청이 기존 데이터를 덮어쓰지 않는다
        assertThat(userStore.size()).isEqualTo(1);
    }
    @Test
    @DisplayName("서로 다른 이메일로 가입하면 각각 다른 userId를 받는다")
    void signUpMultipleUsers() {
        SignUpResult first = signUpHandler.handle(new SignUpCommand(EMAIL, PASSWORD));
        SignUpResult second = signUpHandler.handle(
                new SignUpCommand("other@runiverse.com", PASSWORD));
        assertThat(second.userId()).isNotEqualTo(first.userId());
        assertThat(userStore.size()).isEqualTo(2);
    }
    @Test
    @DisplayName("이메일 형식이 잘못되면 도메인 예외가 그대로 전파되고 저장되지 않는다")
    void signUpWithInvalidEmail() {
        assertThatThrownBy(() -> signUpHandler.handle(new SignUpCommand("not-an-email", PASSWORD)))
                .isInstanceOf(InvalidEmailFormatException.class);
        assertThat(userStore.size()).isZero();
    }
}
