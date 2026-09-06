package com.runiverse.running_service.application.scheduling.command.schedule;

import com.runiverse.running_service.domain.scheduling.vo.ScheduledJobType;

import java.time.LocalDateTime;

public record ScheduleJobCommand(ScheduledJobType type, Long targetId,
                                 LocalDateTime executeAt) {

}
