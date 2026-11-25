package com.runiverse.running_service.application.scheduling;

import com.runiverse.running_service.domain.scheduling.vo.JobTarget;
import com.runiverse.running_service.domain.scheduling.vo.ScheduledJobType;

import java.time.LocalDateTime;
import java.util.Optional;

// 타입별로 "그 시각에 무엇을 할지"를 구현한다.
// 예약 실행기는 선점까지만 책임지고 무엇을 하는지는 모른다 —
// 타입이 늘어도 실행기는 그대로고 이 구현만 늘어난다
public interface ScheduledJobExecutor {

    ScheduledJobType type();

    // 끝나지 않은 작업이면 다시 실행할 시각을 돌려준다 — 예약 실행기가 같은 예약을 그 시각으로 다시 건다.
    // 비어 있으면 끝났다(완료로 적는다). 대부분의 타입은 한 번으로 끝난다
    Optional<LocalDateTime> execute(JobTarget target);
}
