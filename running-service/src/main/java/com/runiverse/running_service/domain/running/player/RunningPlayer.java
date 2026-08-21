package com.runiverse.running_service.domain.running.player;

import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.domain.running.player.exception.InvalidPlayerStatusTransitionException;
import com.runiverse.running_service.domain.running.player.exception.PlayerAlreadyLeftException;
import com.runiverse.running_service.domain.running.room.exception.StartAtRequiredException;
import com.runiverse.running_service.domain.running.player.vo.DesiredPlayerCount;
import com.runiverse.running_service.domain.running.metric.vo.Distance;
import com.runiverse.running_service.domain.running.metric.vo.Pace;
import com.runiverse.running_service.domain.running.player.vo.RunningPlayerId;
import com.runiverse.running_service.domain.running.player.vo.RunningPlayerStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Getter
public class RunningPlayer {

    private final RunningPlayerId runningPlayerId;   // 저장 전에는 null
    private final UserId userId;                     // 논리 참조 — 탈퇴 시 앱이 지운다
    private final Pace avgPace;                      // 매칭 희망 페이스
    private final Distance targetDistance;           // 목표 거리 — 실적(total_*)과 다른 값이다
    private final DesiredPlayerCount desiredPlayerCount;           // 희망 매칭 인원 — 아직 합류 조건은 아니다
    private final LocalDateTime startAt;             // 희망 시작 시각(예약 매칭)
    private RunningPlayerStatus status;
    private LocalDateTime deletedAt;

    @Builder
    private RunningPlayer(Long runningPlayerId, UUID userId, RunningPlayerStatus status,
                          int avgPace, int targetDistance, Integer desiredPlayerCount,
                          LocalDateTime startAt, LocalDateTime deletedAt) {
        this.runningPlayerId = runningPlayerId == null ? null : new RunningPlayerId(runningPlayerId);
        this.userId = new UserId(userId);
        this.status = status == null ? RunningPlayerStatus.JOINED : status;
        this.avgPace = new Pace(avgPace);
        this.targetDistance = new Distance(targetDistance);
        this.desiredPlayerCount = desiredPlayerCount == null ? DesiredPlayerCount.defaultCount() :
                new DesiredPlayerCount(desiredPlayerCount);

        if (startAt == null) {
            throw new StartAtRequiredException();
        }
        this.startAt = startAt;
        this.deletedAt = deletedAt;
    }

    // 매칭 신청 = 이 row가 생기는 것 (솔로도 같은 경로를 탄다)
    public static RunningPlayer request(UUID userId, int avgPace, int targetDistance,
                                        LocalDateTime startAt) {
        return builder()
                .userId(userId)
                .avgPace(avgPace)
                .targetDistance(targetDistance)
                .startAt(startAt)
                .build();
    }

    // 대기 취소 — 상태는 그대로 두고 신청만 끝낸다(이탈과 달리 방 이력을 남길 필요가 없다)
    public void cancel(LocalDateTime canceledAt) {
        ensureActive();
        if (status != RunningPlayerStatus.JOINED && status != RunningPlayerStatus.INVITED) {
            throw new InvalidPlayerStatusTransitionException();
        }
        this.deletedAt = canceledAt;
    }

    // 이탈 — 페널티 여부는 이 시점에 판정돼 상태로 굳는다(별도 페널티 테이블 없음)
    public void leave(boolean penalty, LocalDateTime leftAt) {
        ensureActive();
        this.status = status.leaveWith(penalty);
        this.deletedAt = leftAt;
    }

    public void start() {
        ensureActive();
        this.status = status.transitionTo(RunningPlayerStatus.RUNNING);
    }

    public void complete(LocalDateTime completedAt) {
        ensureActive();
        this.status = status.transitionTo(RunningPlayerStatus.COMPLETED);
        this.deletedAt = completedAt;
    }

    // 매칭 후보 스캔·중복 신청 검사가 항상 함께 보는 조건
    public boolean isActive() {
        return deletedAt == null;
    }

    public boolean isNew() {
        return runningPlayerId == null;
    }

    public Optional<RunningPlayerId> getRunningPlayerId() {
        return Optional.ofNullable(runningPlayerId);
    }

    public Optional<LocalDateTime> getDeletedAt() {
        return Optional.ofNullable(deletedAt);
    }

    // 끝난 신청은 상태만 봐서는 살아 있어 보인다 — 취소가 status를 JOINED로 남기기 때문
    private void ensureActive() {
        if (!isActive()) {
            throw new PlayerAlreadyLeftException();
        }
    }
}
