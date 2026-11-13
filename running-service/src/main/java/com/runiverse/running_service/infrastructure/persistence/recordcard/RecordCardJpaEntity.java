package com.runiverse.running_service.infrastructure.persistence.recordcard;

import com.runiverse.running_service.domain.recordcard.vo.MusicStatus;
import com.runiverse.running_service.domain.recordcard.vo.RecordCardActivityType;
import com.runiverse.running_service.domain.recordcard.vo.RecordCardVisibility;
import com.runiverse.running_service.infrastructure.persistence.common.BaseTimeEntity;
import com.runiverse.running_service.infrastructure.persistence.running.RunningRecordJpaEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Check;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Entity
@Table(
        name = "record_cards",
        // 기록과 1:1 — 지운 카드도 자리를 차지해 다시 만들면 그 행을 되살린다.
        // 카드 쓰기(작성·되살리기·수정·삭제)는 모두 running_records 행을 먼저 잠가 줄 세운다 — 처음 만들 때는
        // 잠글 카드 행이 없고 초안마다 카드 ID가 달라서다. 이 제약은 잠금이 빠졌을 때의 마지막 안전장치다
        uniqueConstraints = @UniqueConstraint(name = "uk_record_card_running_record", columnNames = "running_record_id"),
        // 살아 있는 카드끼리 작성자별 순위가 겹치지 않는다 — 지운 카드는 모두 -1이라 부분 인덱스로 뺀다.
        // 목록(10-3)의 순위 역순·커서 조회 인덱스를 겸한다. options는 DDL 끝에 그대로 붙는다(JPA 3.2)
        indexes = @Index(name = "uk_record_card_user_display_order", columnList = "user_id, display_order",
                unique = true, options = "where deleted_at is null")
)
@Check(name = "ck_record_card_visibility", constraints = "visibility in ('FRIENDS', 'PUBLIC', 'PRIVATE')")
@Check(name = "ck_record_card_base_music_status", constraints = "base_music_status in ('GENERATING', 'COMPLETED', 'FAILED')")
// 음악 key는 AI 서버가 완료 알림으로 준다 — 생성이 끝난(COMPLETED) 기본곡에만 있다
@Check(name = "ck_record_card_base_music_key",
        constraints = "(base_music_status = 'COMPLETED') = (base_music_key is not null)")
@Check(name = "ck_record_card_activity_type", constraints = "activity_type in ('DAILY', 'TRAVEL', 'RACE')")
@Check(name = "ck_record_card_memo_length", constraints = "char_length(memo) between 1 and 500")
@Check(name = "ck_record_card_display_order",
        constraints = "(deleted_at is null and display_order >= 1) or (deleted_at is not null and display_order = -1)")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RecordCardJpaEntity extends BaseTimeEntity {

    // DB가 매기지 않는다 — 작성 요청(9-1)이 정한 UUIDv7을 그대로 넣는다. 사진 key의 카드 폴더이고 AI 서버도 이 값으로 저장한다
    @Id
    @Column(name = "record_card_id", nullable = false, updatable = false)
    private UUID recordCardId;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "running_record_id", nullable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_record_card_running_record"))
    @OnDelete(action = OnDeleteAction.CASCADE)
    private RunningRecordJpaEntity runningRecord;

    // 작성자 — 논리 참조다. 탈퇴해도 카드는 남는다
    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "visibility", nullable = false, length = 20)
    private RecordCardVisibility visibility;

    @Enumerated(EnumType.STRING)
    @Column(name = "activity_type", nullable = false, length = 20)
    private RecordCardActivityType activityType;

    @Enumerated(EnumType.STRING)
    @Column(name = "base_music_status", nullable = false, length = 20)
    private MusicStatus baseMusicStatus;

    @Column(name = "memo", nullable = false, columnDefinition = "text")
    private String memo;

    // 1이 가장 오래된 카드, 지우면 -1
    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    // 이 카드 사진 key 하나를 복사해 둔다 — 목록(10-3)이 사진 테이블을 읽지 않게. 사진이 없으면 null
    @Column(name = "thumbnail_image_key", length = 255)
    private String thumbnailImageKey;

    // AI 서버가 완료 알림으로 준 key — COMPLETED일 때만 있다
    @Column(name = "base_music_key", length = 255)
    private String baseMusicKey;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    private RecordCardJpaEntity(UUID recordCardId, RunningRecordJpaEntity runningRecord, UUID userId) {
        this.recordCardId = recordCardId;
        this.runningRecord = runningRecord;
        this.userId = userId;
    }

    // 처음 만드는 카드 — 나머지 값은 apply가 옮긴다
    public static RecordCardJpaEntity create(UUID recordCardId, RunningRecordJpaEntity runningRecord, UUID userId,
                                             RecordCardVisibility visibility, RecordCardActivityType activityType,
                                             MusicStatus baseMusicStatus, String memo, int displayOrder,
                                             String thumbnailImageKey, String baseMusicKey) {
        RecordCardJpaEntity entity = new RecordCardJpaEntity(recordCardId, runningRecord, userId);
        entity.apply(visibility, activityType, baseMusicStatus, memo, displayOrder, thumbnailImageKey,
                baseMusicKey, null);
        return entity;
    }

    // 도메인이 바꾼 값을 옮긴다 — 되살리기·수정·삭제·음악 완료가 모두 이 길로 온다.
    // 규칙(순위·썸네일·지운 뒤 금지)은 도메인이 이미 지켰다. ID·기록·작성자는 바뀌지 않는다
    public void apply(RecordCardVisibility visibility, RecordCardActivityType activityType,
                      MusicStatus baseMusicStatus, String memo, int displayOrder, String thumbnailImageKey,
                      String baseMusicKey, LocalDateTime deletedAt) {
        this.visibility = visibility;
        this.activityType = activityType;
        this.baseMusicStatus = baseMusicStatus;
        this.memo = memo;
        this.displayOrder = displayOrder;
        this.thumbnailImageKey = thumbnailImageKey;
        this.baseMusicKey = baseMusicKey;
        this.deletedAt = deletedAt;
    }
}
