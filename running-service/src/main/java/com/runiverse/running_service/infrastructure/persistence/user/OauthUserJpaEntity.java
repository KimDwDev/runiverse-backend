package com.runiverse.running_service.infrastructure.persistence.user;

import com.runiverse.running_service.domain.user.vo.Provider;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Entity
@Table(
        name = "oauth_user",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_oauth_user_provider_provider_id",
                columnNames = {"provider", "provider_id"}
        )
)
@IdClass(OauthUserJpaEntity.PK.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OauthUserJpaEntity {
    // PK가 (user_id, provider) 이다
    @Id
    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Id
    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false, updatable = false, length = 20)
    private Provider provider;

    @Column(name = "provider_id", nullable = false, updatable = false, length = 255)
    private String providerId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    private OauthUserJpaEntity(UUID userId, Provider provider, String providerId) {
        this.userId = userId;
        this.provider = provider;
        this.providerId = providerId;
    }

    public static OauthUserJpaEntity create(UUID userId, Provider provider, String providerId) {
        return new OauthUserJpaEntity(userId, provider, providerId);
    }

    // @IdClass 규약
    @Getter
    @NoArgsConstructor
    @EqualsAndHashCode
    public static class PK implements Serializable {
        private UUID userId;
        private Provider provider;
    }
}
