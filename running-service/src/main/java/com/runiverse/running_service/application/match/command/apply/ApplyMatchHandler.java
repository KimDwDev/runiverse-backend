package com.runiverse.running_service.application.match.command.apply;

import com.runiverse.running_service.application.common.port.out.LoadUserAvgPacePort;
import com.runiverse.running_service.application.match.exception.MatchAlreadyInProgressException;
import com.runiverse.running_service.application.match.port.in.ApplyMatchUsecase;
import com.runiverse.running_service.application.match.port.out.CreateMatchApplicationPort;
import com.runiverse.running_service.application.match.port.out.ExistsActiveApplicationPort;
import com.runiverse.running_service.application.user.exception.OnboardingNotCompletedException;
import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.domain.running.metric.vo.Pace;
import com.runiverse.running_service.domain.running.player.RunningPlayer;
import com.runiverse.running_service.domain.running.room.vo.RunningRoomId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class ApplyMatchHandler implements ApplyMatchUsecase {

    private final ExistsActiveApplicationPort existsActiveApplicationPort;
    private final LoadUserAvgPacePort loadUserAvgPacePort;
    private final CreateMatchApplicationPort createMatchApplicationPort;
    private final MatchRoomAssigner matchRoomAssigner;

    @Override
    public ApplyMatchResult handle(ApplyMatchCommand command) {
        UserId userId = new UserId(command.userId());
        // 1. 활성 신청은 하나다 — deleted_at만 보므로 러닝 중(RUNNING)도 여기서 막힌다.
        //    "한 플레이어 = 최대 한 방"은 DB가 강제하지 않는다. 앱이 막는다
        if (existsActiveApplicationPort.existsActive(userId)) {
            throw new MatchAlreadyInProgressException();
        }
        // 2. 페이스는 입력받지 않고 온보딩 값을 쓴다(api-spec 5-A).
        //    온보딩 완료 = user_onboardings row 존재라, 비어 있으면 곧 온보딩 미완료다
        Pace pace = loadUserAvgPacePort.loadAvgPace(userId)
                .orElseThrow(OnboardingNotCompletedException::new);
        // 3. 신청을 먼저 만들어 ID를 확보한다 — 방의 세션이 이 ID로 참조한다
        RunningPlayer player = createMatchApplicationPort.create(RunningPlayer.request(
                command.userId(), pace.secondsPerKm(),
                command.targetDistanceMeters(), command.scheduledStartAt()));
        // 4. 붙을 방을 찾거나 새로 연다
        RunningRoomId roomId = matchRoomAssigner.assign(
                player.getRunningPlayerId().orElseThrow(), pace,
                command.scheduledStartAt(), command.targetDistanceMeters());
        return new ApplyMatchResult(roomId.value());
    }
}
