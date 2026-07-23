package com.runiverse.running_service.infrastructure.persistence.user;

import com.runiverse.running_service.application.user.port.out.CheckEmailDuplicatePort;
import com.runiverse.running_service.application.user.port.out.SaveUserPort;
import com.runiverse.running_service.domain.user.aggregate.User;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class UserPersistenceAdapter implements CheckEmailDuplicatePort, SaveUserPort {

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
}
