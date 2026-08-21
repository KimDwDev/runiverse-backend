package com.runiverse.running_service.infrastructure.persistence.running;

import com.runiverse.running_service.infrastructure.persistence.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Check;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.io.Serializable;

@Getter
@Entity
@Table(
        name = "running_room_sessions",
        // 플레이어 → 배정된 방 역방향 조회 (복합 PK 선두가 running_room_id라 미커버)
        // -> 아마 나중에 유저가 방에 있는지 확인할때 쓰인다.
        indexes = @Index(name = "idx_room_session_player", columnList = "running_player_id")
)
@IdClass(RunningRoomSessionJpaEntity.Pk.class)
@Check(name = "ck_room_session_leave_count", constraints = "leave_count >= 0")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RunningRoomSessionJpaEntity extends BaseTimeEntity {

    @Id
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "running_room_id", nullable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_room_session_room"))
    @OnDelete(action = OnDeleteAction.CASCADE)
    private RunningRoomJpaEntity room;
    // 플레이어 삭제(탈퇴 시 앱이 삭제) 시 링크도 연쇄 삭제
    @Id
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "running_player_id", nullable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_room_session_player"))
    @OnDelete(action = OnDeleteAction.CASCADE)
    private RunningPlayerJpaEntity player;
    // 이 방에서 나간 누적 횟수 — 나갔다 다시 들어오면 또 쌓인다. 페널티 판정 근거
    @Column(name = "leave_count", nullable = false)
    private int leaveCount;
    // 이 방에 남아 있는지 여부 — WS 연결 상태가 아니다.
    // 나가면 false, 다시 들어오면 true. 네트워크가 끊긴 것만으로는 바뀌지 않는다
    @Column(name = "is_connected", nullable = false)
    private boolean connected;

    private RunningRoomSessionJpaEntity(RunningRoomJpaEntity room, RunningPlayerJpaEntity player,
                                        int leaveCount, boolean connected) {
        this.room = room;
        this.player = player;
        this.leaveCount = leaveCount;
        this.connected = connected;
    }

    public static RunningRoomSessionJpaEntity create(RunningRoomJpaEntity room,
                                                     RunningPlayerJpaEntity player,
                                                     int leaveCount, boolean connected) {
        return new RunningRoomSessionJpaEntity(room, player, leaveCount, connected);
    }

    // 프록시여도 식별자는 초기화 없이 읽힌다 — 도메인 복원 시 추가 쿼리가 안 나간다
    public Long playerId() {
        return player.getRunningPlayerId();
    }

    // @IdClass의 필드명은 엔티티의 @Id 필드명과 같고, 타입은 대상 엔티티의 PK 타입이다
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    public static class Pk implements Serializable {

        private Long room;
        private Long player;
    }
}
