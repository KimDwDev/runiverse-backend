package com.runiverse.running_service.infrastructure.persistence.user;

import com.runiverse.running_service.application.auth.port.out.CheckEmailDuplicatePort;
import com.runiverse.running_service.application.auth.port.out.CheckOnboardingPort;
import com.runiverse.running_service.application.auth.port.out.LoadUserByEmailPort;
import com.runiverse.running_service.application.auth.port.out.LoadUserByProviderPort;
import com.runiverse.running_service.application.auth.port.out.SaveUserPort;
import com.runiverse.running_service.application.user.exception.NicknameAlreadyExistsException;
import com.runiverse.running_service.application.user.exception.OnboardingNotCompletedException;
import com.runiverse.running_service.application.user.exception.UserNotFoundException;
import com.runiverse.running_service.application.user.port.out.CheckNicknameDuplicatePort;
import com.runiverse.running_service.application.user.port.out.ClearProfileImagePort;
import com.runiverse.running_service.application.user.port.out.ExistsOnboardingPort;
import com.runiverse.running_service.application.user.port.out.LoadNicknamePort;
import com.runiverse.running_service.application.user.port.out.LoadUserByIdPort;
import com.runiverse.running_service.application.user.port.out.SaveOnboardingPort;
import com.runiverse.running_service.application.user.port.out.UpdateNicknamePort;
import com.runiverse.running_service.application.user.port.out.UpdatePasswordPort;
import com.runiverse.running_service.application.user.port.out.UpdateProfileImagePort;
import com.runiverse.running_service.domain.user.aggregate.User;
import com.runiverse.running_service.domain.user.aggregate.UserOnboarding;
import com.runiverse.running_service.domain.user.vo.Nickname;
import com.runiverse.running_service.domain.user.vo.PasswordHash;
import com.runiverse.running_service.domain.user.vo.ProfileImageKey;
import com.runiverse.running_service.domain.user.vo.Provider;
import com.runiverse.running_service.domain.user.vo.UserId;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class UserPersistenceAdapter implements CheckEmailDuplicatePort, SaveUserPort, LoadUserByEmailPort,
        LoadUserByProviderPort, LoadUserByIdPort, ExistsOnboardingPort, CheckNicknameDuplicatePort, SaveOnboardingPort,
        CheckOnboardingPort, UpdateProfileImagePort, ClearProfileImagePort, LoadNicknamePort, UpdateNicknamePort,
        UpdatePasswordPort {

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
                user.getProfileImageKey().map(ProfileImageKey::value).orElse(null),
                user.getProfileVisibility(),
                emptyToNull(user.getIntroduction().value())
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

    @Override
    public void updateProfileImage(UserId userId, ProfileImageKey profileImageKey) {
        UserJpaEntity entity = entityManager.find(UserJpaEntity.class, userId.value());
        if (entity == null) {
            throw new UserNotFoundException();
        }
        entity.changeProfileImageKey(profileImageKey.value());

    }

    @Override
    public void clearProfileImage(UserId userId) {
        UserJpaEntity entity = entityManager.find(UserJpaEntity.class, userId.value());
        if (entity == null) {
            throw new UserNotFoundException();
        }
        entity.changeProfileImageKey(null); // 변경 감지로 user.profile_image_key = null이 된다.
    }

    @Override
    public void updatePassword(UserId userId, PasswordHash passwordHash) {
        UserJpaEntity entity = entityManager.find(UserJpaEntity.class, userId.value());
        if (entity == null) {
            throw new UserNotFoundException();
        }
        entity.changePasswordHash(passwordHash.value());
    }

    private User toDomain(UserJpaEntity entity) {
        return new User(
                entity.getUserId(),
                entity.getEmail(),
                Objects.requireNonNullElse(entity.getPasswordHash(), ""),
                entity.isAlertConsent(),
                entity.getProfileImageKey(),
                entity.getProfileVisibility(),
                Objects.requireNonNullElse(entity.getIntroduction(), "")
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
                                FROM UserOnboardingJpaEntity o
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
                                FROM UserOnboardingJpaEntity o
                                WHERE o.nickname = :nickname
                                """, Long.class
                )
                .setParameter("nickname", nickname.value())
                .getSingleResult();
        return count > 0;
    }

    @Override
    public void saveOnboarding(UserOnboarding onboarding) {
        entityManager.persist(UserOnboardingJpaEntity.create(
                onboarding.getUserId().value(),
                onboarding.getNickname().value(),
                onboarding.getGender(),
                onboarding.getBirthday().value(),
                onboarding.getAvgPace().secondPerKm(),
                onboarding.getWeight().value(),
                onboarding.getHeight().value()
        ));
    }

    @Override
    public Optional<Nickname> loadNickname(UserId userId) {
        return Optional.ofNullable(entityManager.find(UserOnboardingJpaEntity.class, userId.value()))
                .map(UserOnboardingJpaEntity::getNickname)
                .map(Nickname::new);
    }

    @Override
    public void updateNickname(UserId userId, Nickname nickname) {
        UserOnboardingJpaEntity entity = entityManager.find(UserOnboardingJpaEntity.class, userId.value());
        if (entity == null) {
            throw new OnboardingNotCompletedException();
        }
        entity.changeNickname(nickname.value());
        try {
            // 커밋을 미루면 유니크 위반이 밖에 터진다. -> 잡지 못한 것이기 떄문에 오류로 처리
            entityManager.flush();
        } catch (PersistenceException e) {
            throw new NicknameAlreadyExistsException();
        }
    }
}
