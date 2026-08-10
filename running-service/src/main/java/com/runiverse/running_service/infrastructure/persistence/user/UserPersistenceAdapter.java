package com.runiverse.running_service.infrastructure.persistence.user;

import com.runiverse.running_service.application.auth.port.out.CheckEmailDuplicatePort;
import com.runiverse.running_service.application.auth.port.out.CheckOnboardPort;
import com.runiverse.running_service.application.auth.port.out.LoadUserByEmailPort;
import com.runiverse.running_service.application.auth.port.out.LoadUserByProviderPort;
import com.runiverse.running_service.application.auth.port.out.SaveUserPort;
import com.runiverse.running_service.application.user.port.out.CheckNicknameDuplicatePort;
import com.runiverse.running_service.application.user.port.out.ExistsOnboardPort;
import com.runiverse.running_service.application.user.port.out.LoadUserByIdPort;
import com.runiverse.running_service.application.user.port.out.SaveOnboardPort;
import com.runiverse.running_service.domain.user.aggregate.User;
import com.runiverse.running_service.domain.user.aggregate.UserOnboard;
import com.runiverse.running_service.domain.user.vo.Nickname;
import com.runiverse.running_service.domain.user.vo.Provider;
import com.runiverse.running_service.domain.user.vo.UserId;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class UserPersistenceAdapter implements CheckEmailDuplicatePort, SaveUserPort, LoadUserByEmailPort,
        LoadUserByProviderPort, LoadUserByIdPort, ExistsOnboardPort, CheckNicknameDuplicatePort, SaveOnboardPort,
        CheckOnboardPort {

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
    public Optional<User> loadById(UserId userId) {
        return Optional.ofNullable(entityManager.find(UserJpaEntity.class, userId.value()))
                .map(this::toDomain);
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

    @Override
    public boolean existsByUserId(UserId userId) {
        Long count = entityManager.createQuery(
                        """
                                SELECT COUNT(o)
                                FROM UserOnboardJpaEntity o
                                WHERE o.userId = :userId
                                """, Long.class
                )
                .setParameter("userId", userId.value())
                .getSingleResult();
        return count > 0;
    }

    @Override
    public boolean existsByNickname(Nickname nickname) {
        Long count = entityManager.createQuery(
                        """
                                SELECT COUNT(o)
                                FROM UserOnboardJpaEntity o
                                WHERE o.nickname = :nickname
                                """, Long.class
                )
                .setParameter("nickname", nickname.value())
                .getSingleResult();
        return count > 0;
    }

    @Override
    public void saveOnboard(UserOnboard onboard) {
        entityManager.persist(UserOnboardJpaEntity.create(
                onboard.getUserId().value(),
                onboard.getNickname().value(),
                onboard.getGender(),
                onboard.getBirthday().value(),
                onboard.getAvgPace().secondPerKm(),
                onboard.getWeight().value(),
                onboard.getHeight().value()
        ));
    }
}
