package com.runiverse.running_service.application.running.command.activity;

import java.util.List;

// 홈 공지 띠에 실을 러닝 현황 — 누가 보든 같다
public record RunningActivity(
        int runningNowCount,
        List<FinishedRun> todayFinishedRuns,   // 오늘 완주 최신순
        int todayFinishedCount,
        int yesterdayFinishedCount
) {

    public record FinishedRun(
            Long runningRecordId,
            String nickname,   // 첫 글자만 보이고 나머지는 가린다
            int distanceMeters
    ) {

    }
}
