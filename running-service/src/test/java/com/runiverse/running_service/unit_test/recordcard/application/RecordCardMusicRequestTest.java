package com.runiverse.running_service.unit_test.recordcard.application;

import com.runiverse.running_service.application.recordcard.port.out.RecordCardMusicRequest;
import com.runiverse.running_service.application.recordcard.port.out.RecordCardMusicRequest.Photo;
import com.runiverse.running_service.application.recordcard.port.out.RecordCardMusicRequest.RunningSummary;
import com.runiverse.running_service.domain.recordcard.RecordCard;
import com.runiverse.running_service.domain.recordcard.RecordCard.ImageDraft;
import com.runiverse.running_service.domain.recordcard.vo.DisplayOrder;
import com.runiverse.running_service.domain.recordcard.vo.RecordCardActivityType;
import com.runiverse.running_service.domain.recordcard.vo.RecordCardVisibility;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("기록 카드 음악 생성 요청 단위 테스트")
class RecordCardMusicRequestTest {

    private static final UUID CARD_ID = UUID.fromString("01a0f2d4-6180-7000-8000-4466554400c8");
    private static final UUID USER_ID = UUID.fromString("01a0f2d4-6180-7000-8000-446655440000");
    private static final UUID IMAGE_ID_1 = UUID.fromString("01a0f2d4-6180-7000-8000-44665544012d");
    private static final UUID IMAGE_ID_2 = UUID.fromString("01a0f2d4-6180-7000-8000-44665544013e");
    private static final RunningSummary SUMMARY = new RunningSummary(5000, 1800, 360, null,
            LocalDateTime.of(2026, 10, 19, 6, 30), 61, new BigDecimal("15.0"), null);

    @Test
    @DisplayName("방금 만든 카드의 작성자·카드·회고 글·활동 유형과 기록 요약을 담는다")
    void carriesCardAndSummary() {
        // given
        RecordCard card = card(List.of());

        // when
        RecordCardMusicRequest request = RecordCardMusicRequest.of(card, SUMMARY);

        // then
        assertThat(request.userId()).isEqualTo(USER_ID);
        assertThat(request.recordCardId()).isEqualTo(CARD_ID);
        assertThat(request.memo()).isEqualTo("한강 한 바퀴");
        assertThat(request.activityType()).isEqualTo(RecordCardActivityType.TRAVEL);
        assertThat(request.runningSummary()).isSameAs(SUMMARY);
    }

    @Test
    @DisplayName("사진은 카드 순서대로 담고, 설명이 없으면 null이다")
    void photosInCardOrder() {
        // given
        RecordCard card = card(List.of(
                new ImageDraft(IMAGE_ID_1, "record-cards/u/c/1/first.jpg", "출발선"),
                new ImageDraft(IMAGE_ID_2, "record-cards/u/c/2/second.png", null)));

        // when
        RecordCardMusicRequest request = RecordCardMusicRequest.of(card, SUMMARY);

        // then
        assertThat(request.photos()).containsExactly(
                new Photo(IMAGE_ID_1, "record-cards/u/c/1/first.jpg", "출발선"),
                new Photo(IMAGE_ID_2, "record-cards/u/c/2/second.png", null));
    }

    @Test
    @DisplayName("사진이 없으면 빈 목록이다 — 기본곡만 요청한다")
    void noPhotos() {
        // when
        RecordCardMusicRequest request = RecordCardMusicRequest.of(card(List.of()), SUMMARY);

        // then
        assertThat(request.photos()).isEmpty();
    }

    @Test
    @DisplayName("사진 목록은 복사해 둬 바깥에서 바꿔도 요청이 달라지지 않는다")
    void photosAreCopied() {
        // given
        List<Photo> photos = new ArrayList<>(List.of(new Photo(IMAGE_ID_1, "k", null)));
        RecordCardMusicRequest request = new RecordCardMusicRequest(USER_ID, CARD_ID, "m",
                RecordCardActivityType.DAILY, SUMMARY, photos);

        // when
        photos.clear();

        // then
        assertThat(request.photos()).hasSize(1);
        assertThatThrownBy(() -> request.photos().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    private static RecordCard card(List<ImageDraft> drafts) {
        return RecordCard.create(CARD_ID, 501L, USER_ID, RecordCardVisibility.PUBLIC, RecordCardActivityType.TRAVEL,
                "한강 한 바퀴", DisplayOrder.FIRST, drafts, null);
    }
}
