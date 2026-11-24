package com.runiverse.running_service.application.running.common;

import com.runiverse.running_service.application.running.exception.RunningFinishInProgressException;
import com.runiverse.running_service.application.running.port.out.FinishLeasePort;
import com.runiverse.running_service.application.running.port.out.GpsTrackUpload;
import com.runiverse.running_service.application.running.port.out.LoadWeatherPort;
import com.runiverse.running_service.application.running.port.out.RecordRunningMetricPort;
import com.runiverse.running_service.application.running.port.out.SaveGpsTrackPort;
import com.runiverse.running_service.application.running.port.out.TrackPoint;
import com.runiverse.running_service.application.running.port.out.Weather;
import com.runiverse.running_service.domain.common.vo.UserId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

// 참가자 한 명의 러닝을 확정한다 — RUNNING_FINISH와 목표 도달 자동 종료가 같은 규칙으로 끝나도록 한곳에 둔다.
// 임대 → 판정(잠금) → 봉인(잠금 밖: 분석·S3·날씨) → 확정(잠금) 순서다(D-82).
// 트랜잭션은 여기서 열지 않는다 — 판정·확정이 각자 짧게 열고, 봉인은 잠금·커넥션 없이 돈다.
// 잠금을 쥔 채 S3·날씨를 기다리면 끝내기가 몰릴 때 커넥션 풀이 바닥난다(run 14)
@Slf4j
@Component
@RequiredArgsConstructor
public class RunningFinisher {

    private final FinishLeasePort finishLeasePort;
    private final RunningFinishSteps runningFinishSteps;
    private final SaveGpsTrackPort saveGpsTrackPort;
    private final LoadWeatherPort loadWeatherPort;
    private final RunningFinishProperties runningFinishProperties;
    private final RecordRunningMetricPort recordRunningMetricPort;

    // forced=false는 사용자가 다 뛰었다고 보고 누른 종료다 — 확정 거리가 모자라면 확정하지 않고
    // 남은 거리를 돌려준다. 그대로 확정하면 화면으로는 다 뛴 사용자의 러닝이 조기 종료로 남는다.
    // forced=true(조기 종료를 고른 종료·강제 종료 시각)는 그 시점 거리로 바로 확정한다
    public GoalCheck finish(Long runningRoomId, UUID userIdValue, boolean forced) {
        return run(runningRoomId, userIdValue, false, !forced);
    }

    // 목표 도달 자동 종료 — 확정 거리도 목표에 닿았을 때만 끝낸다.
    // 러닝 중 누적과 확정 거리는 어긋날 수 있다 — 누적만 보고 끝내면 확정 거리가 목표 직전으로 나와
    // 서버가 끊은 러닝이 조기 종료로 남는다. 못 미치면 아무것도 바꾸지 않는다
    public GoalCheck finishOnGoal(Long runningRoomId, UUID userIdValue) {
        return run(runningRoomId, userIdValue, true, true);
    }

    // 이미 끝나 있었던 경우도 reached다 — RUNNING_FINISHED를 다시 보내 클라가 로컬 트랙을 지우게 한다
    private GoalCheck run(Long runningRoomId, UUID userIdValue, boolean onGoal, boolean deferIfShort) {
        UserId userId = new UserId(userIdValue);
        // 아무것도 쥐지 않은 채 잡는다 — 잠금을 쥔 채 임대를 기다리는 곳이 없어 교착이 없다.
        // 판정 트랜잭션 안에서 잡으면 그 커밋이 실패할 때 토큰을 잃어 TTL까지 끝내기가 막힌다
        Optional<String> token = finishLeasePort.tryAcquire(runningRoomId, userId);
        if (token.isEmpty()) {
            return inProgress(runningRoomId, userIdValue, onGoal);
        }
        try {
            FinishJudgement judgement = runningFinishSteps.judge(runningRoomId, userIdValue, onGoal, deferIfShort);
            return switch (judgement) {
                case FinishJudgement.AlreadyFinished ignored -> GoalCheck.reached();
                case FinishJudgement.Pending pending -> GoalCheck.pending(pending.remainingMeters());
                case FinishJudgement.Reached reached -> runningFinishSteps.confirm(
                        runningRoomId, userIdValue, seal(runningRoomId, userIdValue, reached));
            };
        } finally {
            // 확정이 커밋된 뒤이거나 어느 단계가 실패한 뒤다 — 실패한 임대가 TTL까지 끝내기를 막지 않게 바로 놓는다
            release(runningRoomId, userId, token.get());
        }
    }

    // 다른 연결(다시 붙기 전 소켓 등)이 같은 러닝을 끝내는 중이다 — 기다리지 않고 바로 돌려보낸다.
    // 자동 종료에 ERROR를 보내면 클라가 로컬 트랙 전체를 다시 보낸다 — 알리지 않고 다음 배치가 다시 본다.
    // 사용자 종료는 RUNNING_TRACK_UNAVAILABLE로 재전송을 받는다 — 그때는 앞 요청이 끝나 ack가 나간다
    private GoalCheck inProgress(Long runningRoomId, UUID userId, boolean onGoal) {
        log.info("[러닝] 종료 건너뜀: 다른 연결이 종료 처리 중 - roomId={}, userId={}, trigger={}",
                runningRoomId, userId, onGoal ? "GOAL" : "FINISH");
        recordRunningMetricPort.finishInProgress(onGoal);
        if (onGoal) {
            return GoalCheck.inProgress();
        }
        throw new RunningFinishInProgressException();
    }

    // 봉인 — 판정의 스냅숏만 쓴다. 입력(스냅숏·고정된 목표 거리·설정)이 잠금이 지킬 공유 상태가 아니라
    // 잠금 밖에서 계산해도 결과가 같다(D-82). 기록을 만들 수 없으면 S3·날씨도 부르지 않는다
    private Optional<SealedRecord> seal(Long runningRoomId, UUID userId, FinishJudgement.Reached reached) {
        Optional<TrackAnalysis> analysis = TrackAnalyzer.analyze(reached.filtered(),
                reached.analysisTargetMeters(), reached.weightKg(), runningFinishProperties);
        if (analysis.isEmpty()) {
            return Optional.empty();
        }
        TrackAnalysis result = analysis.get();
        // 원본 트랙은 목표 이후 좌표까지 그대로 올린다 — 끊는 것은 기록뿐이다.
        // 키가 러닝 시작 시각으로 정해져 재시도해도 같은 객체를 덮어쓴다
        String gpsTrackKey = saveGpsTrackPort.save(new GpsTrackUpload(
                runningRoomId, userId, result.startAt(), result.endAt(), reached.track().raw()));
        // 날씨는 출발 지점·출발 시각 기준이다 — 조회에 실패해도 어댑터가 기본값을 준다
        TrackPoint origin = reached.track().points().get(0);
        Weather weather = loadWeatherPort.load(origin.latitude(), origin.longitude(), result.startAt());
        return Optional.of(new SealedRecord(result, gpsTrackKey, weather, reached.weightKg()));
    }

    // 해제 실패로 이미 커밋한 종료를 실패로 만들지 않는다 — 임대는 TTL이 지나면 풀린다
    private void release(Long runningRoomId, UserId userId, String token) {
        try {
            finishLeasePort.release(runningRoomId, userId, token);
        } catch (RuntimeException e) {
            log.warn("[러닝] 종료 임대 해제 실패: TTL까지 남음 - roomId={}, userId={}",
                    runningRoomId, userId.value(), e);
        }
    }
}
