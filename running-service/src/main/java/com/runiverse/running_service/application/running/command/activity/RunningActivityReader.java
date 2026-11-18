package com.runiverse.running_service.application.running.command.activity;

import com.runiverse.running_service.application.match.common.MatchProperties;
import com.runiverse.running_service.application.running.port.out.FinishedRunRow;
import com.runiverse.running_service.application.running.port.out.LoadRunningActivityPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

// 홈 공지 띠의 숫자를 한 호출로 읽는다. 날짜는 앱 시간대의 0시로 자른다.
// 트랜잭션을 여기서 연다: 전송 핸들러에 걸면 연결이 없는 인스턴스도 주기마다 DB 커넥션을 잡는다
@Component
@RequiredArgsConstructor
public class RunningActivityReader {

    // 한 줄에 몇 초씩 돌려 보여 주므로 많으면 요약 줄까지 너무 오래 걸린다
    private static final int LATEST_FINISHED_RUN_LIMIT = 3;
    private static final String NICKNAME_MASK = "**";

    private final LoadRunningActivityPort loadRunningActivityPort;
    private final MatchProperties matchProperties;

    @Transactional(readOnly = true)
    public RunningActivity read() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime today = now.toLocalDate().atStartOfDay();
        LocalDateTime tomorrow = today.plusDays(1);
        LocalDateTime yesterday = today.minusDays(1);
        // 솔로는 서버가 강제 종료하지 않아 앱이 죽으면 RUNNING이 영영 남는다 —
        // 매칭·초대 방이 강제 종료되는 시간이 지난 러닝은 달리는 중으로 세지 않는다
        int runningNowCount = loadRunningActivityPort.countRunningPlayers(
                now.minus(matchProperties.forceFinishOffset()));
        return new RunningActivity(
                runningNowCount,
                loadRunningActivityPort.loadLatestFinishedRuns(today, tomorrow, LATEST_FINISHED_RUN_LIMIT).stream()
                        .map(RunningActivityReader::toFinishedRun)
                        .toList(),
                loadRunningActivityPort.countFinishers(today, tomorrow),
                loadRunningActivityPort.countFinishers(yesterday, today));
    }

    private static RunningActivity.FinishedRun toFinishedRun(FinishedRunRow row) {
        return new RunningActivity.FinishedRun(row.runningRecordId(), mask(row.nickname()), row.distanceMeters());
    }

    // 길이를 드러내지 않게 뒤는 늘 같은 개수로 가린다. 닉네임은 한 글자가 한 문자인 글자만 받는다
    private static String mask(String nickname) {
        return nickname.substring(0, 1) + NICKNAME_MASK;
    }
}
