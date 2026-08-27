package com.runiverse.running_service.application.running.command.finish;

import com.runiverse.running_service.application.running.exception.NotRoomPlayerException;
import com.runiverse.running_service.application.running.exception.RunningNotStartableException;
import com.runiverse.running_service.application.running.exception.RunningRoomNotFoundException;
import com.runiverse.running_service.application.running.port.in.FinishRunningUsecase;
import com.runiverse.running_service.application.running.port.out.DeleteRunningTrackPort;
import com.runiverse.running_service.application.running.port.out.LoadRoomPlayerPort;
import com.runiverse.running_service.application.running.port.out.LoadRunningRoomPort;
import com.runiverse.running_service.application.running.port.out.LoadRunningTrackPort;
import com.runiverse.running_service.application.running.port.out.LoadUserWeightPort;
import com.runiverse.running_service.application.running.port.out.RunningTrack;
import com.runiverse.running_service.application.running.port.out.UpdateRunningPlayerPort;
import com.runiverse.running_service.application.user.exception.OnboardingNotCompletedException;
import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.domain.running.metric.vo.Distance;
import com.runiverse.running_service.domain.running.player.RunningPlayer;
import com.runiverse.running_service.domain.running.player.vo.RunningPlayerStatus;
import com.runiverse.running_service.domain.running.room.RunningRoom;
import com.runiverse.running_service.domain.running.room.vo.RunningRoomId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional
public class FinishRunningHandler implements FinishRunningUsecase {

    private final LoadRunningRoomPort loadRunningRoomPort;
    private final LoadRoomPlayerPort loadRoomPlayerPort;
    private final LoadRunningTrackPort loadRunningTrackPort;
    private final LoadUserWeightPort loadUserWeightPort;
    private final UpdateRunningPlayerPort updateRunningPlayerPort;
    private final DeleteRunningTrackPort deleteRunningTrackPort;
    private final RunningFinishProperties properties;

    @Override
    public void handle(FinishRunningCommand command) {
        RunningRoomId roomId = new RunningRoomId(command.runningRoomId());
        UserId userId = new UserId(command.userId());
        // 1. 활성 신청이 아니라 이 방의 참가자를 찾는다 — 이미 끝난 참가자도 찾아야 멱등이 된다
        RunningPlayer player = loadRoomPlayerPort.load(roomId, userId)
                .orElseThrow(NotRoomPlayerException::new);
        // 이미 확정된 참가자 - 기록을 덮어쓰지 않고 트랙만 정리한 뒤 ack를 다시 보낸다
        if (!player.isActive()) {
            deleteRunningTrackPort.delete(command.runningRoomId(), userId);
            return;
        }
        // RUNNING_START를 거치지 않은 참가자는 확정할 러닝이 없다.
        // 도메인 예외가 아니라 여기서 거른다 — 도메인 예외는 500으로 마스킹된다
        if (player.getStatus() != RunningPlayerStatus.RUNNING) {
            throw new RunningNotStartableException();
        }
        // 2. 목표 거리는 참가자가 아니라 방이 정한다 —
        //    참가자별 목표로 나누면 같은 방에서 splitNumber N이 서로 다른 구간을 가리킨다
        RunningRoom room = loadRunningRoomPort.loadById(roomId)
                .orElseThrow(RunningRoomNotFoundException::new);
        // 온보딩에서 몸무게는 필수다 — 비어 있으면 러닝을 시작할 수 없었어야 할 사용자다
        BigDecimal weightKg = loadUserWeightPort.loadWeightKg(userId)
                .orElseThrow(OnboardingNotCompletedException::new);

        // 3. 마지막 수신 좌표까지로 지표를 낸다.
        //    산출할 수 없는 트랙이면 실제 거리를 0으로 보고 상태만 확정한다(feature-spec §2)
        RunningTrack track = loadRunningTrackPort.load(command.runningRoomId(), userId);
        Optional<TrackAnalysis> analysis = TrackAnalyzer.analyze(
                track.points(), analysisTargetMeters(room), weightKg, properties);
        // 4. 상태를 확정한다 — 기록 저장은 3단계에서 이 사이에 붙는다
        finish(player, room, analysis.map(TrackAnalysis::totalDistanceMeters).orElse(0));
        updateRunningPlayerPort.update(player);
        deleteRunningTrackPort.delete(command.runningRoomId(), userId);
    }

    // 솔로 방은 목표 거리가 없다 — 상한을 넘겨 실측 트랙을 자르지 않고 그대로 분석한다
    private int analysisTargetMeters(RunningRoom room) {
        return room.getTargetDistance().orElseGet(Distance::unlimited).meters();
    }

    // 확정 거리로만 판정한다. command.forced()는 조기 종료 '의사'일 뿐
    // 최종 상태를 정하지 않는다(api-spec 5-D)
    private void finish(RunningPlayer player, RunningRoom room, int totalDistanceMeters) {
        LocalDateTime finishedAt = LocalDateTime.now();
        Optional<Distance> target = room.getTargetDistance();
        // 목표가 없는 솔로 러닝은 사용자가 끝낸 것이 곧 완주다 — 비율을 잴 기준이 없다
        if (target.isEmpty() || totalDistanceMeters >= target.get().meters()) {
            player.complete(finishedAt);
            return;
        }
        double ratio = (double) totalDistanceMeters / target.get().meters();
        player.leave(ratio < properties.penaltyDistanceRatio(), finishedAt);
    }
}
