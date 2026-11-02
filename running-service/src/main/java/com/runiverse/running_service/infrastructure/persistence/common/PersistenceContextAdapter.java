package com.runiverse.running_service.infrastructure.persistence.common;

import com.runiverse.running_service.application.common.port.out.FlushPendingChangesPort;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PersistenceContextAdapter implements FlushPendingChangesPort {

    private final EntityManager entityManager;

    @Override
    public void flushPendingChanges() {
        entityManager.flush();
    }
}
