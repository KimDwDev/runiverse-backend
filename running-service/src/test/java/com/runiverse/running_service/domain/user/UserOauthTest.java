package com.runiverse.running_service.domain.user;

import com.runiverse.running_service.domain.user.aggregate.User;
import com.runiverse.running_service.domain.user.exception.InvalidUserIdFormatException;
import com.runiverse.running_service.domain.user.exception.LastSignInMethodException;
import com.runiverse.running_service.domain.user.exception.OauthAlreadyLinkedException;
import com.runiverse.running_service.domain.user.exception.OauthNotLinkedException;
import com.runiverse.running_service.domain.user.exception.UnsupportedProviderException;
import com.runiverse.running_service.domain.user.vo.Provider;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.UUID;

public class UserOauthTest {

    private static final UUID USER_ID = UUID.fromString("0190a5b4-3c2d-7e1f-8a2b-123456789abc");
    private static final String EMAIL = "user@example.com";
    private static final String PASSWORD_HASH =
            "$argon2id$v=19$m=65536,t=3,p=1$"
                    + "c29tZXNhbHQ$"
                    + "c29tZWhhc2h2YWx1ZQ";
    private static final String KAKAO_ID = "3812345678";
    private static final String GOOGLE_ID = "109876543210987654321";

    @Nested
    @DisplayName("소셜 회원가입 테스트")
    class RegisterWithOauthTest {

        @Test
        @DisplayName("소셜 회원가입 시 유저 생성과 연결이 함께 이루어진다")
        void registerWithOauthSuccess() {
            // when
            User user = User.registerWithOauth(USER_ID, EMAIL, "kakao", KAKAO_ID);

            // then -> 연결 없는 소셜 유저가 만들어지면 로그인할 방법이 없다
            assertThat(user.getUserId().value()).isEqualTo(USER_ID);
            assertThat(user.getEmail().value()).isEqualTo(EMAIL);
            assertThat(user.getPasswordHash().value()).isEmpty();
            assertThat(user.getOauthUsers()).hasSize(1);
            assertThat(user.hasProvider(Provider.KAKAO)).isTrue();
        }

        @Test
        @DisplayName("지원하지 않는 provider면 회원가입에 실패한다")
        void registerWithUnsupportedProviderFails() {
            // when & then
            assertThatThrownBy(() -> User.registerWithOauth(USER_ID, EMAIL, "facebook", KAKAO_ID))
                    .isInstanceOf(UnsupportedProviderException.class);
        }

        @Test
        @DisplayName("UUIDv7이 아니면 회원가입에 실패한다")
        void registerWithInvalidUserIdFails() {
            // given
            UUID uuidV4 = UUID.randomUUID();

            // when & then
            assertThatThrownBy(() -> User.registerWithOauth(uuidV4, EMAIL, "kakao", KAKAO_ID))
                    .isInstanceOf(InvalidUserIdFormatException.class);
        }
    }

    @Nested
    @DisplayName("소셜 연결 테스트")
    class LinkOauthTest {

        @Test
        @DisplayName("로컬 가입 유저에 소셜 계정을 연결할 수 있다")
        void linkOauthToLocalUserSuccess() {
            // given
            User user = new User(USER_ID, EMAIL, PASSWORD_HASH);

            // when
            user.linkOauth("kakao", KAKAO_ID);

            // then
            assertThat(user.getOauthUsers()).hasSize(1);
            assertThat(user.hasProvider(Provider.KAKAO)).isTrue();
        }

        @Test
        @DisplayName("서로 다른 provider는 함께 연결할 수 있다")
        void linkMultipleProvidersSuccess() {
            // given
            User user = User.registerWithOauth(USER_ID, EMAIL, "kakao", KAKAO_ID);

            // when
            user.linkOauth("google", GOOGLE_ID);

            // then
            assertThat(user.getOauthUsers()).hasSize(2);
            assertThat(user.hasProvider(Provider.KAKAO)).isTrue();
            assertThat(user.hasProvider(Provider.GOOGLE)).isTrue();
        }

        @Test
        @DisplayName("이미 연결된 provider를 다시 연결하면 예외가 발생한다")
        void linkDuplicateProviderFails() {
            // given -> 한 유저는 같은 provider를 두 번 연결할 수 없다
            User user = User.registerWithOauth(USER_ID, EMAIL, "kakao", KAKAO_ID);

            // when & then
            assertThatThrownBy(() -> user.linkOauth("kakao", "9999999999"))
                    .isInstanceOf(OauthAlreadyLinkedException.class)
                    .hasMessage("이미 연결된 소셜 계정입니다.");

            assertThat(user.getOauthUsers()).hasSize(1);
        }

        @Test
        @DisplayName("provider 대소문자가 달라도 중복으로 판정된다")
        void linkDuplicateProviderIgnoringCaseFails() {
            // given
            User user = User.registerWithOauth(USER_ID, EMAIL, "kakao", KAKAO_ID);

            // when & then
            assertThatThrownBy(() -> user.linkOauth("KAKAO", "9999999999"))
                    .isInstanceOf(OauthAlreadyLinkedException.class);
        }
    }

    @Nested
    @DisplayName("소셜 연결 해제 테스트")
    class UnlinkOauthTest {

        @Test
        @DisplayName("비밀번호가 있으면 마지막 소셜 연결도 해제할 수 있다")
        void unlinkLastOauthWithPasswordSuccess() {
            // given
            User user = new User(USER_ID, EMAIL, PASSWORD_HASH);
            user.linkOauth("kakao", KAKAO_ID);

            // when
            user.unlinkOauth("kakao");

            // then -> 비밀번호로 로그인할 수 있으니 계정이 잠기지 않는다
            assertThat(user.getOauthUsers()).isEmpty();
            assertThat(user.hasProvider(Provider.KAKAO)).isFalse();
        }

        @Test
        @DisplayName("소셜 전용 유저도 연결이 둘 이상이면 해제할 수 있다")
        void unlinkOneOfMultipleOauthSuccess() {
            // given
            User user = User.registerWithOauth(USER_ID, EMAIL, "kakao", KAKAO_ID);
            user.linkOauth("google", GOOGLE_ID);

            // when
            user.unlinkOauth("kakao");

            // then
            assertThat(user.getOauthUsers()).hasSize(1);
            assertThat(user.hasProvider(Provider.KAKAO)).isFalse();
            assertThat(user.hasProvider(Provider.GOOGLE)).isTrue();
        }

        @Test
        @DisplayName("소셜 전용 유저의 마지막 연결은 해제할 수 없다")
        void unlinkLastSignInMethodFails() {
            // given -> 해제하면 아무 방법으로도 로그인할 수 없게 된다
            User user = User.registerWithOauth(USER_ID, EMAIL, "kakao", KAKAO_ID);

            // when & then
            assertThatThrownBy(() -> user.unlinkOauth("kakao"))
                    .isInstanceOf(LastSignInMethodException.class)
                    .hasMessage("마지막 로그인 수단은 해제할 수 없습니다.");

            assertThat(user.getOauthUsers()).hasSize(1);
        }

        @Test
        @DisplayName("연결된 적 없는 provider를 해제하면 예외가 발생한다")
        void unlinkNotLinkedProviderFails() {
            // given
            User user = User.registerWithOauth(USER_ID, EMAIL, "kakao", KAKAO_ID);

            // when & then -> 존재 확인이 먼저라 '마지막 수단' 예외가 아니어야 한다
            assertThatThrownBy(() -> user.unlinkOauth("google"))
                    .isInstanceOf(OauthNotLinkedException.class)
                    .hasMessage("연결되지 않은 소셜 계정입니다.");
        }

        @Test
        @DisplayName("지원하지 않는 provider를 해제하면 예외가 발생한다")
        void unlinkUnsupportedProviderFails() {
            // given
            User user = User.registerWithOauth(USER_ID, EMAIL, "kakao", KAKAO_ID);

            // when & then
            assertThatThrownBy(() -> user.unlinkOauth("facebook"))
                    .isInstanceOf(UnsupportedProviderException.class);
        }
    }

    @Nested
    @DisplayName("컬렉션 캡슐화 테스트")
    class EncapsulationTest {

        @Test
        @DisplayName("연결 목록은 외부에서 수정할 수 없다")
        void oauthUsersIsUnmodifiable() {
            // given -> 여기서 수정이 되면 linkOauth의 검증을 우회할 수 있다
            User user = User.registerWithOauth(USER_ID, EMAIL, "kakao", KAKAO_ID);

            // when & then
            assertThatThrownBy(() -> user.getOauthUsers().clear())
                    .isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        @DisplayName("연결이 없는 유저의 목록은 비어 있다")
        void localUserHasNoOauthUsers() {
            // given
            User user = new User(USER_ID, EMAIL, PASSWORD_HASH);

            // when & then
            assertThat(user.getOauthUsers()).isEmpty();
            assertThat(user.hasProvider(Provider.KAKAO)).isFalse();
        }
    }
}
