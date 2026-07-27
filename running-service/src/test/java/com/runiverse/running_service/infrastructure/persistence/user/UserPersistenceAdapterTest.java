package com.runiverse.running_service.infrastructure.persistence.user;

import com.github.f4b6a3.uuid.UuidCreator;
import com.runiverse.running_service.domain.user.aggregate.User;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class UserPersistenceAdapterTest {

    // PasswordHash VO가 Argon2id 형식만 허용하므로 형식에 맞는 값을 쓴다
    private static final String PASSWORD_HASH =
            "$argon2id$v=19$m=16384,t=2,p=1$c29tZXNhbHQ$aGFzaHZhbHVl";

    @Mock
    private EntityManager entityManager;

    @Mock
    private TypedQuery<Long> countQuery;

    @Mock
    private TypedQuery<UserJpaEntity> userQuery;

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

    @Test
    @DisplayName("이메일에 해당하는 유저를 도메인 User로 변환해 반환한다")
    void loadByEmailReturnsUser() {
        // given
        String email = "test@example.com";
        UUID userId = UuidCreator.getTimeOrderedEpoch();

        UserJpaEntity entity = UserJpaEntity.create(
                userId, email, PASSWORD_HASH, true, "러닝을 좋아합니다"
        );

        givenUserQueryReturns(email, Stream.of(entity));

        // when
        Optional<User> result = userPersistenceAdapter.loadByEmail(email);

        // then
        assertThat(result).isPresent();

        User user = result.get();
        assertThat(user.getUserId().value()).isEqualTo(userId);
        assertThat(user.getEmail().value()).isEqualTo(email);
        assertThat(user.getPasswordHash().value()).isEqualTo(PASSWORD_HASH);
        assertThat(user.isAlertConsent()).isTrue();
        assertThat(user.getDescription().value()).isEqualTo("러닝을 좋아합니다");
    }

    @Test
    @DisplayName("이메일에 해당하는 유저가 없으면 빈 Optional을 반환한다")
    void loadByEmailReturnsEmpty() {
        // given
        String email = "none@example.com";

        givenUserQueryReturns(email, Stream.empty());

        // when
        Optional<User> result = userPersistenceAdapter.loadByEmail(email);

        // then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("description이 null이면 빈 문자열로 변환한다")
    void loadByEmailConvertsNullDescription() {
        // given
        String email = "test@example.com";

        UserJpaEntity entity = UserJpaEntity.create(
                UuidCreator.getTimeOrderedEpoch(), email, PASSWORD_HASH, false, null
        );

        givenUserQueryReturns(email, Stream.of(entity));

        // when
        Optional<User> result = userPersistenceAdapter.loadByEmail(email);

        // then
        assertThat(result).isPresent();
        assertThat(result.get().getDescription().value()).isEmpty();
    }

    private void givenUserQueryReturns(String email, Stream<UserJpaEntity> found) {
        when(entityManager.createQuery(anyString(), eq(UserJpaEntity.class)))
                .thenReturn(userQuery);

        when(userQuery.setParameter("email", email))
                .thenReturn(userQuery);

        when(userQuery.getResultStream())
                .thenReturn(found);
    }

}
