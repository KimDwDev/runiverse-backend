package com.runiverse.running_service.infrastructure.persistence.user;

import com.runiverse.running_service.application.user.port.out.CheckEmailDuplicatePort;
import com.runiverse.running_service.application.user.port.out.LoadUserByEmailPort;
import com.runiverse.running_service.application.user.port.out.SaveUserPort;
import com.runiverse.running_service.domain.user.aggregate.User;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class UserPersistenceAdapter implements CheckEmailDuplicatePort, SaveUserPort, LoadUserByEmailPort {

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
                user.getPasswordHash().value(),
                user.isAlertConsent(),
                user.getDescription().value()
        );

        entityManager.persist(entity);

        return user;
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
                entity.getPasswordHash(),
                entity.isAlertConsent(),
                Objects.requireNonNullElse(entity.getDescription(), "")
        );
    }
}
