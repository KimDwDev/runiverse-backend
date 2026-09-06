package com.runiverse.running_service.application.scheduling.port.in;

import com.runiverse.running_service.application.scheduling.command.schedule.ScheduleJobCommand;

public interface ScheduleJobUsecase {

    // 예약 모듈이 밖에 내주는 유일한 입구 — 부르는 쪽은 저장·타이머·전파를 알지 않는다
    void handle(ScheduleJobCommand command);
}
