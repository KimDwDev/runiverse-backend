package com.runiverse.running_service.domain.user.aggregate;

import com.runiverse.running_service.domain.user.exception.InvalidNicknameFormatException;
import com.runiverse.running_service.domain.user.exception.InvalidNicknameLengthException;
import com.runiverse.running_service.domain.user.exception.UnsupportedGenderException;
import com.runiverse.running_service.domain.user.exception.UserIdRequiredException;
import com.runiverse.running_service.domain.user.vo.Gender;
import com.runiverse.running_service.domain.user.vo.UserId;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public class UserOnboardTest {

    private static final UserId USER_ID =
            new UserId(UUID.fromString("0190a5b4-3c2d-7e1f-8a2b-123456789abc"));
    private static final String NICKNAME = "러너킴";
    private static final String GENDER = "MALE";
    private static final LocalDate BIRTHDAY = LocalDate.of(1999, 5, 20);
    private static final int AVG_PACE = 330;
    private static final BigDecimal WEIGHT = new BigDecimal("70.5");
    private static final BigDecimal HEIGHT = new BigDecimal("175.0");

    private UserOnboard newOnboard() {
        return new UserOnboard(USER_ID, NICKNAME, GENDER, BIRTHDAY, AVG_PACE, WEIGHT, HEIGHT);
    }

    @Nested
    @DisplayName("온보딩 생성 테스트")
    class CreateTest {

        @Test
        @DisplayName("원시 값이 모두 VO로 감싸진다")
        void createUserOnboardSuccess() {
            // when
            UserOnboard onboard = newOnboard();

            // then
            assertThat(onboard.getUserId()).isEqualTo(USER_ID);
            assertThat(onboard.getNickname().value()).isEqualTo(NICKNAME);
            assertThat(onboard.getGender()).isEqualTo(Gender.MALE);
            assertThat(onboard.getBirthday().value()).isEqualTo(BIRTHDAY);
            assertThat(onboard.getAvgPace().secondPerKm()).isEqualTo(AVG_PACE);
            assertThat(onboard.getWeight().value()).isEqualByComparingTo(WEIGHT);
            assertThat(onboard.getHeight().value()).isEqualByComparingTo(HEIGHT);
        }

        @Test
        @DisplayName("userId가 null이면 예외가 발생한다")
        void createWithNullUserIdFails() {
            // when & then
            assertThatThrownBy(() ->
                    new UserOnboard(null, NICKNAME, GENDER, BIRTHDAY, AVG_PACE, WEIGHT, HEIGHT))
                    .isInstanceOf(UserIdRequiredException.class)
                    .hasMessage("사용자 ID는 필수입니다.");
        }

        @Test
        @DisplayName("잘못된 값이 있으면 해당 VO의 예외가 발생한다")
        void createWithInvalidValueFails() {
            // when & then -> 검증이 VO에 위임되어 있는지 확인한다
            assertThatThrownBy(() ->
                    new UserOnboard(USER_ID, "김", GENDER, BIRTHDAY, AVG_PACE, WEIGHT, HEIGHT))
                    .isInstanceOf(InvalidNicknameLengthException.class);

            assertThatThrownBy(() ->
                    new UserOnboard(USER_ID, NICKNAME, "UNKNOWN", BIRTHDAY, AVG_PACE, WEIGHT, HEIGHT))
                    .isInstanceOf(UnsupportedGenderException.class);
        }
    }

    @Nested
    @DisplayName("온보딩 수정 테스트")
    class ChangeTest {

        @Test
        @DisplayName("change는 원본을 바꾸지 않고 새 인스턴스를 반환한다")
        void changeReturnsNewInstance() {
            // given
            UserOnboard origin = newOnboard();

            // when
            UserOnboard changed = origin.change("새러너", null, null, null, null, null);

            // then -> final 필드로 둔 불변 설계가 실제로 지켜지는지 확인한다
            assertThat(origin.getNickname().value()).isEqualTo(NICKNAME);
            assertThat(changed.getNickname().value()).isEqualTo("새러너");
            assertThat(changed).isNotSameAs(origin);
        }

        @Test
        @DisplayName("null인 항목은 기존 값을 유지한다")
        void changeKeepsNullFields() {
            // given
            UserOnboard origin = newOnboard();

            // when -> 닉네임만 바꾼다
            UserOnboard changed = origin.change("새러너", null, null, null, null, null);

            // then
            assertThat(changed.getGender()).isEqualTo(origin.getGender());
            assertThat(changed.getBirthday()).isEqualTo(origin.getBirthday());
            assertThat(changed.getAvgPace()).isEqualTo(origin.getAvgPace());
            assertThat(changed.getWeight()).isEqualTo(origin.getWeight());
            assertThat(changed.getHeight()).isEqualTo(origin.getHeight());
        }

        @Test
        @DisplayName("모든 항목을 한 번에 바꿀 수 있다")
        void changeAllFields() {
            // given
            UserOnboard origin = newOnboard();

            // when
            UserOnboard changed = origin.change(
                    "새러너", "FEMALE", LocalDate.of(2000, 1, 1),
                    400, new BigDecimal("60.0"), new BigDecimal("165.0"));

            // then
            assertThat(changed.getNickname().value()).isEqualTo("새러너");
            assertThat(changed.getGender()).isEqualTo(Gender.FEMALE);
            assertThat(changed.getBirthday().value()).isEqualTo(LocalDate.of(2000, 1, 1));
            assertThat(changed.getAvgPace().secondPerKm()).isEqualTo(400);
            assertThat(changed.getWeight().value()).isEqualByComparingTo("60.0");
            assertThat(changed.getHeight().value()).isEqualByComparingTo("165.0");
        }

        @Test
        @DisplayName("수정해도 userId는 유지된다")
        void changeKeepsUserId() {
            // given
            UserOnboard origin = newOnboard();

            // when
            UserOnboard changed = origin.change("새러너", null, null, null, null, null);

            // then -> 식별자가 바뀌면 다른 사람의 온보딩이 된다
            assertThat(changed.getUserId()).isEqualTo(USER_ID);
        }

        @Test
        @DisplayName("잘못된 값으로 수정하면 예외가 발생하고 원본은 그대로다")
        void changeWithInvalidValueFails() {
            // given
            UserOnboard origin = newOnboard();

            // when & then
            assertThatThrownBy(() -> origin.change("러너!!", null, null, null, null, null))
                    .isInstanceOf(InvalidNicknameFormatException.class);

            assertThat(origin.getNickname().value()).isEqualTo(NICKNAME);
        }
    }

    @Nested
    @DisplayName("동등성 테스트")
    class EqualityTest {

        @Test
        @DisplayName("userId가 같으면 값이 달라도 같은 온보딩이다")
        void equalsByUserId() {
            // given
            UserOnboard origin = newOnboard();
            UserOnboard changed = origin.change("완전히다른닉", "FEMALE", null, null, null, null);

            // when & then -> 식별자는 userId 하나뿐이다
            assertThat(changed).isEqualTo(origin);
            assertThat(changed).hasSameHashCodeAs(origin);
        }

        @Test
        @DisplayName("userId가 다르면 다른 온보딩이다")
        void notEqualsByDifferentUserId() {
            // given
            UserId otherId =
                    new UserId(UUID.fromString("0190a5b4-3c2d-7e1f-8a2b-cba987654321"));

            // when & then
            assertThat(new UserOnboard(otherId, NICKNAME, GENDER, BIRTHDAY, AVG_PACE, WEIGHT, HEIGHT))
                    .isNotEqualTo(newOnboard());
        }
    }
}
