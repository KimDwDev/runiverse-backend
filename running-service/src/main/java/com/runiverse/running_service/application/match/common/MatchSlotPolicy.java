package com.runiverse.running_service.application.match.common;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

// 매칭 신청 선택지 판정. 신청 검증과 슬롯 조회가 이 판정을 함께 써야 두 API의 선택지가 갈라지지 않는다
@Component
@RequiredArgsConstructor
public class MatchSlotPolicy {

    private final MatchProperties matchProperties;

    // 범위 안에서 첫 시작 시각부터 간격만큼 떨어진 시각만 받는다 — 19:00:30처럼 초가 붙어도 걸린다
    public boolean allowsStartAt(LocalDateTime startAt) {
        LocalTime time = startAt.toLocalTime();
        return !time.isBefore(matchProperties.allowedFirstStartTime())
                && !time.isAfter(matchProperties.allowedLastStartTime())
                && isOnInterval(time);
    }

    public boolean allowsDistance(int distanceMeters) {
        return matchProperties.allowedDistancesMeters().contains(distanceMeters);
    }

    // 그날의 신청 가능 시각, 오름차순
    public List<LocalDateTime> startTimesOf(LocalDate date) {
        List<LocalDateTime> startTimes = new ArrayList<>();
        LocalDateTime last = date.atTime(matchProperties.allowedLastStartTime());
        for (LocalDateTime startAt = date.atTime(matchProperties.allowedFirstStartTime());
             !startAt.isAfter(last);
             startAt = startAt.plus(matchProperties.allowedStartTimeInterval())) {
            startTimes.add(startAt);
        }
        return startTimes;
    }

    private boolean isOnInterval(LocalTime time) {
        return Duration.between(matchProperties.allowedFirstStartTime(), time).toNanos()
                % matchProperties.allowedStartTimeInterval().toNanos() == 0;
    }
}
