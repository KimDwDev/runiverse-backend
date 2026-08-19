package com.runiverse.running_service.domain.running.aggregate;

import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.domain.running.exception.StartAtRequiredException;
import com.runiverse.running_service.domain.running.vo.DesiredPlayerCount;
import com.runiverse.running_service.domain.running.vo.Distance;
import com.runiverse.running_service.domain.running.vo.Pace;
import com.runiverse.running_service.domain.running.vo.RunningPlayerId;
import com.runiverse.running_service.domain.running.vo.RunningPlayerStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
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
}
