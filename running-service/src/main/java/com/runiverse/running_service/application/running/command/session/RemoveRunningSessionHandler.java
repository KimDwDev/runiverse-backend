package com.runiverse.running_service.application.running.command.session;

import com.runiverse.running_service.application.running.port.in.RemoveRunningSessionUsecase;
import com.runiverse.running_service.application.running.port.out.RunningSessionPort;
import com.runiverse.running_service.domain.common.vo.UserId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RemoveRunningSessionHandler implements RemoveRunningSessionUsecase {

    private final RunningSessionPort runningSessionPort;

    @Override
    public void handle(RemoveRunningSessionCommand command) {
        runningSessionPort.remove(new UserId(command.userId()), command.connection());
    }
}
