package com.runiverse.running_service.unit_test.running.presentation;

import com.github.f4b6a3.uuid.UuidCreator;
import com.runiverse.running_service.presentation.running.request.InviteFriendsRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

// DTO는 목록의 모양만 본다. 친구 여부·활성 신청·중복은 InviteFriendsHandler가 본다 — 문구는 api-spec 4-2 그대로다
@DisplayName("친구 초대 요청 DTO 검증 단위 테스트")
class InviteFriendsRequestValidationTest {

    private static final String REQUIRED = "초대할 친구는 필수입니다.";
    private static final String SIZE = "초대할 친구는 1명 이상 50명 이하여야 합니다.";
    private static final String INVALID = "입력값이 올바르지 않습니다.";

    private static ValidatorFactory validatorFactory;
    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void closeValidator() {
        validatorFactory.close();
    }

    private static List<UUID> ids(int count) {
        return IntStream.range(0, count).mapToObj(i -> UuidCreator.getTimeOrderedEpoch()).toList();
    }

    private static List<String> messagesOf(InviteFriendsRequest request) {
        return validator.validate(request).stream().map(ConstraintViolation::getMessage).toList();
    }

    @ParameterizedTest(name = "{0}명")
    @ValueSource(ints = {1, 50})
    @DisplayName("1명부터 50명까지 받는다")
    void sizeBoundsAreInclusive(int count) {
        // when & then
        assertThat(messagesOf(new InviteFriendsRequest(ids(count)))).isEmpty();
    }

    @Test
    @DisplayName("같은 사람이 여러 번 들어 있어도 DTO에서는 거절하지 않는다")
    void duplicatesPass() {
        // given -> 중복은 하나로 본다(api-spec 4-2) — 오류가 아니다
        UUID same = UuidCreator.getTimeOrderedEpoch();

        // when & then
        assertThat(messagesOf(new InviteFriendsRequest(List.of(same, same)))).isEmpty();
    }

    @Test
    @DisplayName("목록이 빠지면 필수 위반 하나만 낸다")
    void nullListReportsOnlyRequired() {
        // when & then -> 크기 검증은 null을 통과시켜 문장이 겹치지 않는다
        assertThat(messagesOf(new InviteFriendsRequest(null))).containsExactly(REQUIRED);
    }

    @ParameterizedTest(name = "{0}명")
    @ValueSource(ints = {0, 51})
    @DisplayName("0명이거나 50명을 넘으면 범위 위반이다")
    void sizeOutOfRangeIsRejected(int count) {
        // when & then
        assertThat(messagesOf(new InviteFriendsRequest(ids(count)))).containsExactly(SIZE);
    }

    @Test
    @DisplayName("목록 안에 빈칸이 섞이면 거절한다")
    void nullElementIsRejected() {
        // given -> 걸러내지 않으면 UserId 생성에서 도메인 예외가 나 500이 된다
        List<UUID> withNull = new ArrayList<>(Arrays.asList(UuidCreator.getTimeOrderedEpoch(), null));

        // when & then
        assertThat(messagesOf(new InviteFriendsRequest(withNull))).containsExactly(INVALID);
    }
}
