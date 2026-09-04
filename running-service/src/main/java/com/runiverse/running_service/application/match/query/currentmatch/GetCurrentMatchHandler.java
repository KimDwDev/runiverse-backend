package com.runiverse.running_service.application.match.query.currentmatch;

import com.runiverse.running_service.application.match.MatchProperties;
import com.runiverse.running_service.application.match.port.in.GetCurrentMatchUsecase;
import com.runiverse.running_service.application.match.port.out.LoadMatchRoomPort;
import com.runiverse.running_service.application.match.query.roominfo.RoomInfoAssembler;
import com.runiverse.running_service.application.running.port.out.LoadActiveRunningPlayerPort;
import com.runiverse.running_service.application.running.port.out.LoadRunningRoomPort;
import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.domain.running.player.RunningPlayer;
import com.runiverse.running_service.domain.running.player.vo.RunningPlayerId;
import com.runiverse.running_service.domain.running.player.vo.RunningPlayerStatus;
import com.runiverse.running_service.domain.running.room.RunningRoom;
import com.runiverse.running_service.domain.running.room.vo.RunningRoomId;
import com.runiverse.running_service.domain.running.room.vo.RunningRoomStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GetCurrentMatchHandler implements GetCurrentMatchUsecase {

    private final LoadActiveRunningPlayerPort loadActiveRunningPlayerPort;
    private final LoadMatchRoomPort loadMatchRoomPort;
    private final LoadRunningRoomPort loadRunningRoomPort;
    private final RoomInfoAssembler roomInfoAssembler;
    private final MatchProperties matchProperties;

    @Override
    public GetCurrentMatchResult handle(GetCurrentMatchQuery query) {
        UserId userId = new UserId(query.userId());
        // 1. 활성 신청은 deleted_at IS NULL AND status='JOINED'다(erd.md).
        //    러닝 중(RUNNING)은 매칭 단계가 아니라 NONE이다 — 복귀는 WS 경로가 맡는다.
        //    11번의 중복 신청 차단(deleted_at만 봄)과는 판정 기준이 다르다
        RunningPlayer player = loadActiveRunningPlayerPort.loadActive(userId).orElse(null);
        if (player == null || player.getStatus() != RunningPlayerStatus.JOINED) {
            return GetCurrentMatchResult.none();
        }
        RunningPlayerId playerId = player.getRunningPlayerId().orElseThrow();
        // 2. "방 미배정" 상태는 없다 — 활성 신청이 있으면 방도 반드시 있다(feature-spec).
        //    비어 있으면 조용히 NONE으로 덮지 않는다. 데이터 사고라 드러나야 한다
        RunningRoomId roomId = loadMatchRoomPort.findAssignedRoom(playerId)
                .orElseThrow(() -> new IllegalStateException(
                        "활성 신청에 배정된 방이 없다 — runningPlayerId=" + playerId.value()));
        RunningRoom room = loadRunningRoomPort.loadById(roomId)
                .orElseThrow(() -> new IllegalStateException(
                        "배정된 방을 찾을 수 없다 — runningRoomId=" + roomId.value()));
        return new GetCurrentMatchResult(
                state(room), roomId.value(), roomInfoAssembler.assemble(room));
    }

    private MatchState state(RunningRoom room) {
        // 모집 중이 아니면 전부 확정 이후다. 방이 STARTED인데 본인은 아직 JOINED일 수 있다 —
        // 각자의 RUNNING_START가 자기만 RUNNING으로 바꾸기 때문이다(erd 생명주기).
        // 그 구분은 state가 아니라 RoomInfo.status가 실어 나른다
        if (room.getStatus() != RunningRoomStatus.MATCHING) {
            return MatchState.MATCHED;
        }
        // 마감이 지났는데 스케줄러가 아직 안 닫은 방은 MATCHED로 본다 —
        // 확정은 마감 시각에 일어난 사실이고 스케줄러는 반영이 늦을 뿐이다(api-spec 5-A)
        LocalDateTime closeAt = room.getStartAt().minus(matchProperties.closeOffset());
        return LocalDateTime.now().isBefore(closeAt) ? MatchState.WAITING : MatchState.MATCHED;
    }
}
