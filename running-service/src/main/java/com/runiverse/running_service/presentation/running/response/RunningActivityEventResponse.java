package com.runiverse.running_service.presentation.running.response;

import com.runiverse.running_service.application.running.command.activity.RunningActivity;

import java.util.List;

// 앱 스트림 HOME_ACTIVITY 이벤트의 data — 지금 달리는 사람 수와 오늘·어제 완주 소식
public record RunningActivityEventResponse(
        int runningNowCount,
        List<FinishedRunResponse> todayFinishedRuns,   // 최신순
        int todayFinishedCount,
        int yesterdayFinishedCount
) {

    public static RunningActivityEventResponse from(RunningActivity activity) {
        return new RunningActivityEventResponse(
                activity.runningNowCount(),
                activity.todayFinishedRuns().stream().map(FinishedRunResponse::from).toList(),
                activity.todayFinishedCount(),
                activity.yesterdayFinishedCount());
    }

    public record FinishedRunResponse(
            Long runningRecordId,
            String nickname,
            int distanceMeters
    ) {

        static FinishedRunResponse from(RunningActivity.FinishedRun run) {
            return new FinishedRunResponse(run.runningRecordId(), run.nickname(), run.distanceMeters());
        }
    }
}
