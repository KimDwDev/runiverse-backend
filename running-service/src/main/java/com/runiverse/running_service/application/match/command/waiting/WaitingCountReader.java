package com.runiverse.running_service.application.match.command.waiting;

import com.runiverse.running_service.application.match.common.MatchSlotPolicy;
import com.runiverse.running_service.application.match.port.out.CountJoinableWaitingPlayersPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;

// 오늘 신청할 수 있는 칸의 대기 인원을 더한다 — 칸은 신청 화면 슬롯과 같은 정책으로 거른다.
// 트랜잭션을 여기서 연다: 전송 핸들러에 걸면 연결이 없는 인스턴스도 주기마다 DB 커넥션을 잡는다
@Component
@RequiredArgsConstructor
public class WaitingCountReader {

    private final CountJoinableWaitingPlayersPort countJoinableWaitingPlayersPort;
    private final MatchSlotPolicy matchSlotPolicy;

    @Transactional(readOnly = true)
    public int read() {
        // 마감 판정은 한 시각으로 한다 — 칸마다 now()를 부르면 경계에서 이웃 칸이 서로 다른 시각으로 판정된다
        LocalDateTime now = LocalDateTime.now();
        Map<Integer, Map<LocalDateTime, Integer>> counts =
                countJoinableWaitingPlayersPort.countJoinableWaitingPlayers(now.toLocalDate());
        // 선택지 밖 거리·시각의 방은 신청 화면에 칸이 없으므로 세지 않는다
        return counts.entrySet().stream()
                .filter(byDistance -> matchSlotPolicy.allowsDistance(byDistance.getKey()))
                .flatMap(byDistance -> byDistance.getValue().entrySet().stream())
                .filter(byStartAt -> matchSlotPolicy.allowsStartAt(byStartAt.getKey())
                        && matchSlotPolicy.isOpen(byStartAt.getKey(), now))
                .mapToInt(Map.Entry::getValue)
                .sum();
    }
}
