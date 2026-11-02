package com.runiverse.running_service.application.common.port.out;

public interface FlushPendingChangesPort {

    // 지금까지 고친 것을 한 도메인이 아니라 트랜잭션 전체에서 DB에 쓴다. 커밋은 하지 않는다
    void flushPendingChanges();
}
