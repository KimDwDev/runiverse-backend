package com.runiverse.running_service.application.running.command.location;

import com.runiverse.running_service.application.running.port.in.UpdateRunningLocationUsecase;
import com.runiverse.running_service.application.running.port.out.AppendRunningTrackPort;
import com.runiverse.running_service.domain.common.vo.UserId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UpdateRunningLocationHandler implements UpdateRunningLocationUsecase {

    private final AppendRunningTrackPort appendRunningTrackPort;

    @Override
    public void handle(UpdateRunningLocationCommand command) {
        appendRunningTrackPort.append(
                command.runningRoomId(), new UserId(command.userId()), command.points());
    }
}
