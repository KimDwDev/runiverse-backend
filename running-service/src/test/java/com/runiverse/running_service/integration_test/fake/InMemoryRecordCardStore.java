package com.runiverse.running_service.integration_test.fake;

import com.runiverse.running_service.application.recordcard.port.out.CreateRecordCardPort;
import com.runiverse.running_service.application.recordcard.port.out.ExistsOwnRunningRecordPort;
import com.runiverse.running_service.application.recordcard.port.out.FindRecordCardIdByRunningRecordPort;
import com.runiverse.running_service.application.recordcard.port.out.LoadMaxDisplayOrderPort;
import com.runiverse.running_service.application.recordcard.port.out.LoadRecordCardDetailPort;
import com.runiverse.running_service.application.recordcard.port.out.LoadRunningSummaryPort;
import com.runiverse.running_service.application.recordcard.port.out.LockOwnRunningRecordPort;
import com.runiverse.running_service.application.recordcard.port.out.RecordCardDetail;
import com.runiverse.running_service.application.recordcard.port.out.RecordCardMusicRequest.RunningSummary;
import com.runiverse.running_service.domain.common.vo.RunningRecordId;
import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.domain.recordcard.RecordCard;
import com.runiverse.running_service.domain.recordcard.vo.DisplayOrder;
import com.runiverse.running_service.domain.recordcard.vo.MusicKey;
import com.runiverse.running_service.domain.recordcard.vo.RecordCardId;
import com.runiverse.running_service.domain.recordcard.vo.RecordCardImageCaption;
import com.runiverse.running_service.domain.recordcard.vo.RecordCardImageKey;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

// 카드를 붙일 기록과 그 기록의 카드 — 기록 주인·숨긴 방, 카드 ID, 작성자별 순위와 두 UNIQUE만 흉내 낸다.
// 잠금은 흉내 내지 않는다(한 스레드라 줄 설 일이 없다)
public class InMemoryRecordCardStore implements ExistsOwnRunningRecordPort, LockOwnRunningRecordPort,
        FindRecordCardIdByRunningRecordPort, LoadMaxDisplayOrderPort, CreateRecordCardPort, LoadRunningSummaryPort,
        LoadRecordCardDetailPort {

    // 기록마다 같은 수치를 돌려준다 — 요약 값 자체는 어댑터 단위 테스트와 E2E가 본다
    public static final RunningSummary SUMMARY = new RunningSummary(5000, 1800, 360, 168,
            LocalDateTime.of(2026, 10, 19, 6, 30), 61, new BigDecimal("15.0"), 22);
    public static final LocalDateTime CREATED_AT = LocalDateTime.of(2026, 10, 19, 8, 0);

    private record StoredRecord(UUID userId, boolean roomHidden) {

    }

    private final Map<Long, StoredRecord> records = new HashMap<>();
    private final Map<Long, RecordCard> cardsByRecord = new HashMap<>();
    private final Map<Long, UUID> foreignCardIdsByRecord = new HashMap<>();
    private final Map<Long, List<String>> segmentPolylinesByRecord = new HashMap<>();
    private boolean staleMaxOnce;

    @Override
    public boolean existsOwnedBy(RunningRecordId runningRecordId, UserId userId) {
        StoredRecord record = records.get(runningRecordId.value());
        return record != null && record.userId().equals(userId.value()) && !record.roomHidden();
    }

    @Override
    public boolean lockOwnedBy(RunningRecordId runningRecordId, UserId userId) {
        return existsOwnedBy(runningRecordId, userId);
    }

    @Override
    public Optional<RunningSummary> load(RunningRecordId runningRecordId) {
        return records.containsKey(runningRecordId.value()) ? Optional.of(SUMMARY) : Optional.empty();
    }

    @Override
    public Optional<RecordCardId> findIdByRunningRecord(RunningRecordId runningRecordId) {
        RecordCard card = cardsByRecord.get(runningRecordId.value());
        if (card != null) {
            return Optional.of(card.getRecordCardId());
        }
        return Optional.ofNullable(foreignCardIdsByRecord.get(runningRecordId.value())).map(RecordCardId::new);
    }

    @Override
    public Optional<DisplayOrder> loadMax(UserId userId) {
        if (staleMaxOnce) {
            staleMaxOnce = false;
            return Optional.empty();
        }
        return cardsByRecord.values().stream()
                .filter(card -> card.getUserId().equals(userId))
                .map(card -> card.getDisplayOrder().value())
                .max(Integer::compare)
                .map(DisplayOrder::new);
    }

    @Override
    public CreateResult create(RecordCard recordCard) {
        long recordId = recordCard.getRunningRecordId().value();
        if (cardsByRecord.containsKey(recordId) || foreignCardIdsByRecord.containsKey(recordId)) {
            return CreateResult.RECORD_ALREADY_HAS_CARD;
        }
        boolean orderTaken = cardsByRecord.values().stream()
                .anyMatch(card -> card.getUserId().equals(recordCard.getUserId())
                        && card.getDisplayOrder().equals(recordCard.getDisplayOrder()));
        if (orderTaken) {
            return CreateResult.DISPLAY_ORDER_TAKEN;
        }
        cardsByRecord.put(recordId, recordCard);
        return CreateResult.CREATED;
    }

    // 상세(8-1) — 기록 수치는 SUMMARY, 구간 경로는 넣어 둔 것만 돌려준다(없으면 빈 목록)
    @Override
    public Optional<RecordCardDetail> loadDetail(RecordCardId recordCardId) {
        return cardsByRecord.entrySet().stream()
                .filter(entry -> entry.getValue().getRecordCardId().equals(recordCardId))
                .findFirst()
                .map(entry -> toDetail(entry.getKey(), entry.getValue()));
    }

    private RecordCardDetail toDetail(long runningRecordId, RecordCard card) {
        List<RecordCardDetail.Image> images = card.getImages().stream()
                .map(image -> new RecordCardDetail.Image(image.getRecordCardImageId().value(),
                        image.getImageKey().value(), image.getCaption().map(RecordCardImageCaption::value).orElse(null),
                        image.getMusicStatus(), image.getMusicKey().map(MusicKey::value).orElse(null)))
                .toList();
        return new RecordCardDetail(card.getRecordCardId().value(), card.getUserId().value(), card.getVisibility(),
                card.getActivityType(), card.getMemo().value(),
                card.getThumbnailImageKey().map(RecordCardImageKey::value).orElse(null),
                card.getBaseMusicStatus(), card.getBaseMusicKey().map(MusicKey::value).orElse(null), images,
                new RecordCardDetail.Record(runningRecordId, SUMMARY.startAt(), SUMMARY.totalDistance(),
                        SUMMARY.totalDuration(), SUMMARY.avgPace(),
                        segmentPolylinesByRecord.getOrDefault(runningRecordId, List.of())),
                CREATED_AT, CREATED_AT);
    }

    // 아래는 준비·검증 전용
    public void addSegments(long runningRecordId, List<String> segmentPolylines) {
        segmentPolylinesByRecord.put(runningRecordId, List.copyOf(segmentPolylines));
    }

    public void addRecord(long runningRecordId, UUID userId) {
        records.put(runningRecordId, new StoredRecord(userId, false));
    }

    public void addRecordInHiddenRoom(long runningRecordId, UUID userId) {
        records.put(runningRecordId, new StoredRecord(userId, true));
    }

    public void addCard(long runningRecordId, UUID recordCardId) {
        foreignCardIdsByRecord.put(runningRecordId, recordCardId);
    }

    public void hideRoomOf(long runningRecordId) {
        StoredRecord record = records.get(runningRecordId);
        records.put(runningRecordId, new StoredRecord(record.userId(), true));
    }

    // 다른 기록으로 동시에 만든 요청이 아직 커밋되지 않은 카드를 못 보고 옛 최댓값을 읽은 상황 — 다음 한 번만 "카드 없음"으로 읽는다
    public void readStaleMaxOnce() {
        this.staleMaxOnce = true;
    }

    public Optional<RecordCard> cardOf(long runningRecordId) {
        return Optional.ofNullable(cardsByRecord.get(runningRecordId));
    }

    public int cardCount() {
        return cardsByRecord.size();
    }
}
