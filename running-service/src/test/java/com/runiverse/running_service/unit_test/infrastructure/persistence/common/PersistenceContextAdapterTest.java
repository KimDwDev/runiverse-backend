package com.runiverse.running_service.unit_test.infrastructure.persistence.common;

import com.runiverse.running_service.infrastructure.persistence.common.PersistenceContextAdapter;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("영속성 컨텍스트 어댑터 단위 테스트")
class PersistenceContextAdapterTest {

    @Mock
    private EntityManager entityManager;

    @InjectMocks
    private PersistenceContextAdapter persistenceContextAdapter;

    @Test
    @DisplayName("고친 것을 커밋 전에 DB에 쓴다")
    void flushesPendingChanges() {
        // when
        persistenceContextAdapter.flushPendingChanges();

        // then
        verify(entityManager).flush();
    }
}
