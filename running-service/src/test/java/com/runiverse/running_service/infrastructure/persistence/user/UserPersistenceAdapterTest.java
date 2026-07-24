package com.runiverse.running_service.infrastructure.persistence.user;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class UserPersistenceAdapterTest {

    @Mock
    private EntityManager entityManager;

    @Mock
    private TypedQuery<Long> countQuery;

    @InjectMocks
    private UserPersistenceAdapter userPersistenceAdapter;

    @Test
    @DisplayName("동일한 이메일이 존재하면 true를 반환한다")
    void existsByEmailReturnsTrue() {
        // given
        String email = "test@example.com";

        when(entityManager.createQuery(anyString(), eq(Long.class)))
                .thenReturn(countQuery);

        when(countQuery.setParameter("email", email))
                .thenReturn(countQuery);

        when(countQuery.getSingleResult())
                .thenReturn(1L);

        // when
        boolean result = userPersistenceAdapter.existsByEmail(email);

        // then
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("동일한 이메일이 존재하지 않으면 false를 반환한다")
    void existsByEmailReturnsFalse() {
        // given
        String email = "test@example.com";

        when(entityManager.createQuery(anyString(), eq(Long.class)))
                .thenReturn(countQuery);

        when(countQuery.setParameter("email", email))
                .thenReturn(countQuery);

        when(countQuery.getSingleResult())
                .thenReturn(0L);

        // when
        boolean result = userPersistenceAdapter.existsByEmail(email);

        // then
        assertThat(result).isFalse();

        verify(countQuery).setParameter("email", email);
        verify(countQuery).getSingleResult();
    }

}
