package com.runiverse.running_service.unit_test.user.domain.aggregate;

import com.runiverse.running_service.domain.user.exception.ProviderIdRequiredException;
import com.runiverse.running_service.domain.user.exception.ProviderIdTooLongException;
import com.runiverse.running_service.domain.user.vo.ProviderId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class OauthVoTest {

    @Nested
    @DisplayName("ProviderId 테스트")
    class ProviderIdTest {

        private static final String KAKAO_ID = "3812345678";

        @Test
        @DisplayName("소셜 계정 식별자로 ProviderId를 생성할 수 있다")
        void createProviderIdSuccess() {
            // when
            ProviderId providerId = new ProviderId(KAKAO_ID);

            // then
            assertThat(providerId.value()).isEqualTo(KAKAO_ID);
        }

        @Test
        @DisplayName("앞뒤 공백은 제거된다")
        void createProviderIdTrimsWhitespace() {
            // when
            ProviderId providerId = new ProviderId("  " + KAKAO_ID + "  ");

            // then
            assertThat(providerId.value()).isEqualTo(KAKAO_ID);
        }

        @Test
        @DisplayName("공백만 다른 식별자는 같은 값 객체이다")
        void providerIdWithWhitespaceEqualsTrimmedOne() {
            // when & then -> 정규화가 안 되면 조회와 저장이 어긋나 중복 가입이 생긴다
            assertThat(new ProviderId("  " + KAKAO_ID + "  "))
                    .isEqualTo(new ProviderId(KAKAO_ID));
        }

        @Test
        @DisplayName("식별자가 null이면 예외가 발생한다")
        void createProviderIdWithNullFails() {
            // when & then
            assertThatThrownBy(() -> new ProviderId(null))
                    .isInstanceOf(ProviderIdRequiredException.class)
                    .hasMessage("소셜 계정 식별자를 가져오지 못했습니다.");
        }

        @Test
        @DisplayName("식별자가 빈 값이거나 공백뿐이면 예외가 발생한다")
        void createProviderIdWithBlankFails() {
            // when & then
            assertThatThrownBy(() -> new ProviderId(""))
                    .isInstanceOf(ProviderIdRequiredException.class);

            assertThatThrownBy(() -> new ProviderId("   "))
                    .isInstanceOf(ProviderIdRequiredException.class);
        }

        @Test
        @DisplayName("식별자가 255자면 생성할 수 있다")
        void createProviderIdWithMaxLengthSuccess() {
            // given
            String maxLength = "1".repeat(255);

            // when
            ProviderId providerId = new ProviderId(maxLength);

            // then
            assertThat(providerId.value()).hasSize(255);
        }

        @Test
        @DisplayName("식별자가 255자를 초과하면 예외가 발생한다")
        void createProviderIdTooLongFails() {
            // given
            String tooLong = "1".repeat(256);

            // when & then
            assertThatThrownBy(() -> new ProviderId(tooLong))
                    .isInstanceOf(ProviderIdTooLongException.class)
                    .hasMessage("소셜 계정 식별자 길이가 허용 범위를 벗어났습니다.");
        }
    }
}
