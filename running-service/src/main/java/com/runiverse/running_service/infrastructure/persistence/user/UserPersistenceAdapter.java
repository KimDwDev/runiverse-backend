package com.runiverse.running_service.infrastructure.persistence.user;

import com.runiverse.running_service.application.auth.port.out.CheckEmailDuplicatePort;
import com.runiverse.running_service.application.auth.port.out.LoadUserByEmailPort;
import com.runiverse.running_service.application.auth.port.out.LoadUserByProviderPort;
import com.runiverse.running_service.application.auth.port.out.SaveUserPort;
import com.runiverse.running_service.domain.user.aggregate.User;
import com.runiverse.running_service.domain.user.vo.Provider;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class UserPersistenceAdapter implements CheckEmailDuplicatePort, SaveUserPort, LoadUserByEmailPort,
        LoadUserByProviderPort {

    private final EntityManager entityManager;

    @Override
    public boolean existsByEmail(String email) {
        Long count = entityManager.createQuery(
    """
           SELECT COUNT(u)
           FROM UserJpaEntity u 
           WHERE u.email = :email
           """, Long.class
        )
        .setParameter("email", email)
        .getSingleResult();

        return count > 0;
    }

    @Override
    public User save(User user) {
        UserJpaEntity entity = UserJpaEntity.create(
                user.getUserId().value(),
                user.getEmail().value(),
                emptyToNull(user.getPasswordHash().value()),
                user.isAlertConsent(),
                emptyToNull(user.getDescription().value())
        );

        entityManager.persist(entity);
        user.getOauthUser().ifPresent(oauth -> entityManager.persist((
                OauthUserJpaEntity.create(
                        oauth.getUserId().value(),
                        oauth.getProvider(),
                        oauth.getProviderId().value()
                )
                )));
        return user;
    }

    private static String emptyToNull(String value) {
        return (value == null || value.isEmpty()) ? null : value;
    }

    @Override
    public Optional<User> loadByEmail(String email) {
        return entityManager.createQuery(
            """
            SELECT u 
            FROM UserJpaEntity u 
            WHERE u.email = :email
            """, UserJpaEntity.class
        )
                .setParameter("email", email)
                .getResultStream()
                .findFirst()
                .map(this::toDomain);
    }
    private User toDomain(UserJpaEntity entity) {
        return new User(
                entity.getUserId(),
                entity.getEmail(),
                Objects.requireNonNullElse(entity.getPasswordHash(), ""),
                entity.isAlertConsent(),
                Objects.requireNonNullElse(entity.getDescription(), "")
        );
    }

    @Override
    public Optional<User> loadByProvider(Provider provider, String providerId) {
        return entityManager.createQuery(
           """
           SELECT u 
           FROM UserJpaEntity u, OauthUserJpaEntity o
           WHERE o.userId = u.userId
               AND o.provider = :provider
               AND o.providerId = :providerId
           """, UserJpaEntity.class
        )
                .setParameter("provider", provider)
                .setParameter("providerId", providerId)
                .getResultStream()
                .findFirst()
                .map(this::toDomain);
    }
}
