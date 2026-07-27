package com.runiverse.running_service.domain.user;

import com.runiverse.running_service.domain.user.aggregate.User;
import static org.assertj.core.api.Assertions.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.UUID;

public class UserAggregateTest {

    private static final UUID USER_ID = UUID.fromString("0190a5b4-3c2d-7e1f-8a2b-123456789abc");
    private static final String EMAIL = "user@example.com";
    private static final String PASSWORD_HASH =
            "$argon2id$v=19$m=65536,t=3,p=1$"
                    + "c29tZXNhbHQ$"
                    + "c29tZWhhc2h2YWx1ZQ";

    @Nested
    @DisplayName("전체 생성자 테스트")
    class FullConstructorTest {

        @Test
        @DisplayName("모든 정보를 사용해 사용자를 생성할 수 있다.")
        void createUserSuccess() {
            // given
            boolean alertConsent = true;
            String description = "함께 즐겁게 달려요!";

            // when
            User user = new User(
                    USER_ID,
                    EMAIL,
                    PASSWORD_HASH,
                    alertConsent,
                    description
            );

            // then
            assertThat(user.getUserId().value()).isEqualTo(USER_ID);
            assertThat(user.getEmail().value()).isEqualTo(EMAIL);
            assertThat(user.getPasswordHash().value()).isEqualTo(PASSWORD_HASH);
            assertThat(user.isAlertConsent()).isTrue();
            assertThat(user.getDescription().value()).isEqualTo(description);
        }

        @Nested
        @DisplayName("로컬 회원가입 생성자 테스트")
        class LocalSignupConstructorTest {

            @Test
            @DisplayName("로컬 회원가입 시 description은 빈 값으로 생성된다")
            void createLocalUserSuccess() {
                // given
                boolean alertConsent = true;

                // when
                User user = new User(
                        USER_ID,
                        EMAIL,
                        PASSWORD_HASH,
                        alertConsent
                );

                // then
                assertThat(user.getUserId().value()).isEqualTo(USER_ID);
                assertThat(user.getEmail().value()).isEqualTo(EMAIL);
                assertThat(user.getPasswordHash().value()).isEqualTo(PASSWORD_HASH);
                assertThat(user.isAlertConsent()).isTrue();
                assertThat(user.getDescription().value()).isEmpty();
            }
        }

        @Nested
        @DisplayName("OAuth 회원가입 생성자 테스트")
        class OAuthSignupConstructorTest {

            @Test
            @DisplayName("OAuth 회원가입 시 비밀번호 해시와 소개는 빈 값이고 알림 동의는 false이다")
            void createOAuthUserSuccess() {
                // when
                User user = new User(USER_ID, EMAIL);

                // then
                assertThat(user.getUserId().value()).isEqualTo(USER_ID);
                assertThat(user.getEmail().value()).isEqualTo(EMAIL);
                assertThat(user.getPasswordHash().value()).isEmpty();
                assertThat(user.isAlertConsent()).isFalse();
                assertThat(user.getDescription().value()).isEmpty();
            }
        }

        @Nested
        @DisplayName("기본 생성자 테스트")
        class DefaultConstructorTest {

            @Test
            @DisplayName("알림 동의와 소개가 없으면 기본값으로 생성된다")
            void createUserWithDefaultValuesSuccess() {
                // when
                User user = new User(
                        USER_ID,
                        EMAIL,
                        PASSWORD_HASH
                );

                // then
                assertThat(user.getUserId().value()).isEqualTo(USER_ID);
                assertThat(user.getEmail().value()).isEqualTo(EMAIL);
                assertThat(user.getPasswordHash().value()).isEqualTo(PASSWORD_HASH);
                assertThat(user.isAlertConsent()).isFalse();
                assertThat(user.getDescription().value()).isEmpty();
            }
        }

        @Nested
        @DisplayName("VO 검증 위임 테스트")
        class ValidationTest {

            @Test
            @DisplayName("UUIDv7이 아니면 사용자 생성에 실패한다")
            void createUserWithInvalidUserIdFails() {
                // given
                UUID uuidV4 = UUID.randomUUID();

                // when & then
                assertThatThrownBy(
                        () -> new User(
                                uuidV4,
                                EMAIL,
                                PASSWORD_HASH
                        )
                )
                        .isInstanceOf(IllegalArgumentException.class)
                        .hasMessage("사용자 ID는 UUIDv7 형식이어야 합니다.");
            }

            @Test
            @DisplayName("이메일 형식이 올바르지 않으면 사용자 생성에 실패한다")
            void createUserWithInvalidEmailFails() {
                // given
                String invalidEmail = "invalid-email";

                // when & then
                assertThatThrownBy(
                        () -> new User(
                                USER_ID,
                                invalidEmail,
                                PASSWORD_HASH
                        )
                )
                        .isInstanceOf(IllegalArgumentException.class);
            }

            @Test
            @DisplayName("비밀번호 해시 형식이 올바르지 않으면 사용자 생성에 실패한다")
            void createUserWithInvalidPasswordHashFails() {
                // given
                String invalidPasswordHash = "plain-password";

                // when & then
                assertThatThrownBy(
                        () -> new User(
                                USER_ID,
                                EMAIL,
                                invalidPasswordHash
                        )
                )
                        .isInstanceOf(IllegalArgumentException.class);
            }

            @Test
            @DisplayName("소개가 100자를 초과하면 사용자 생성에 실패한다")
            void createUserWithLongDescriptionFails() {
                // given
                String longDescription = "가".repeat(101);

                // when & then
                assertThatThrownBy(
                        () -> new User(
                                USER_ID,
                                EMAIL,
                                PASSWORD_HASH,
                                false,
                                longDescription
                        )
                )
                        .isInstanceOf(IllegalArgumentException.class)
                        .hasMessage("소개는 100자를 초과할 수 없습니다.");
            }
        }
    }
}
