package com.runiverse.running_service.application.running.command.solo;

import com.runiverse.running_service.application.running.port.in.StartSoloRunningUsecase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class StartSoloRunningHandler implements StartSoloRunningUsecase {

    @Override
    public StartSoloRunningResult handle(StartSoloRunningCommand command) {

        return null;
    }
}
