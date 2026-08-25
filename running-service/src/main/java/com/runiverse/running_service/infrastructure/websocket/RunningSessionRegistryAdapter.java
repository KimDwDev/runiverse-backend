package com.runiverse.running_service.infrastructure.websocket;

import com.runiverse.running_service.application.running.port.out.RunningConnection;
import com.runiverse.running_service.application.running.port.out.RunningSessionPort;
import com.runiverse.running_service.domain.common.vo.UserId;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class RunningSessionRegistryAdapter implements RunningSessionPort {

    // 이 인스턴스에 붙어 있는 연결만 담는다 - 다른 서버 것은 없다
    private final Map<UserId, RunningConnection> connectionByUser = new ConcurrentHashMap<>();

}
