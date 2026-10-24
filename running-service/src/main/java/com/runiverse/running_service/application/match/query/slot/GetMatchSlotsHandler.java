package com.runiverse.running_service.application.match.query.slot;

import com.runiverse.running_service.application.match.common.MatchSlotPolicy;
import com.runiverse.running_service.application.match.port.in.GetMatchSlotsUsecase;
import com.runiverse.running_service.application.match.port.out.CountWaitingPlayersPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GetMatchSlotsHandler implements GetMatchSlotsUsecase {

    private final MatchSlotPolicy matchSlotPolicy;
    private final CountWaitingPlayersPort countWaitingPlayersPort;

    @Override
    public GetMatchSlotsResult handle(GetMatchSlotsQuery query) {
        // 1. 대기자가 있는 거리·시각만 온다 — 없는 거리·시각은 아래에서 0으로 채운다
        Map<Integer, Map<LocalDateTime, Integer>> waitingCounts =
                countWaitingPlayersPort.countWaitingPlayers(query.date());
        // 2. 마감 판정은 한 시각으로 한다 — 슬롯마다 now()를 부르면 경계에서 이웃 슬롯이 서로 다른 시각으로 판정된다.
        //    경계는 신청의 MATCH_SLOT_CLOSED와 같아야 해 정책이 함께 판정한다
        LocalDateTime now = LocalDateTime.now();
        List<LocalDateTime> startTimes = matchSlotPolicy.startTimesOf(query.date());
        // 3. 거리는 정책의 선택지를 돈다 — 대기자가 없는 거리도 빠지지 않고, 선택지 밖 거리의 방은 내려가지 않는다
        return new GetMatchSlotsResult(matchSlotPolicy.distances().stream()
                .map(distance -> {
                    Map<LocalDateTime, Integer> countsOfDistance = waitingCounts.getOrDefault(distance, Map.of());
                    return new GetMatchSlotsResult.DistanceSlots(distance, startTimes.stream()
                            .map(startAt -> new GetMatchSlotsResult.MatchSlot(
                                    startAt,
                                    countsOfDistance.getOrDefault(startAt, 0),
                                    matchSlotPolicy.isOpen(startAt, now)))
                            .toList());
                })
                .toList());
    }
}
