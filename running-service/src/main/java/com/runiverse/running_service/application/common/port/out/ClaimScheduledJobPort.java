package com.runiverse.running_service.application.common.port.out;

public interface ClaimScheduledJobPort {

    // 여러 인스턴스가 같은 예약을 들고 있으므로 실행 직전에 하나만 이겨야 한다.
    // is_sent를 조건부로 뒤집어 이겼으면 true — 대상 테이블을 잠그기 전에 걸러진다
    boolean claim(Long scheduledJobId);
}
