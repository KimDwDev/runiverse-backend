package com.runiverse.running_service.infrastructure.persistence.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Entity
@Table(name = "users")
@NoArgsConstructor(access = AccessLevel.PROTECTED) // 다른 객체에서 생성되지 않도록 하는 속성
public class UserJpaEntity {

    @Id
    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(
            name = "email",
            nullable = false,
            unique = true,
            length = 255
    )
    private String email;

    @Column(
            name = "hash_password",
            length = 255
    )
    private String passwordHash;

    @Column(
            name = "alert_consent",
            nullable = false
    )
    private boolean alertConsent;

    @Column(
            name = "description",
            length = 100
    )
    private String description;

    @CreationTimestamp
    @Column(
            name = "created_at",
            nullable = false,
            updatable = false
    )
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(
            name = "updated_at",
            nullable = false
    )
    private LocalDateTime updatedAt;

    private UserJpaEntity(
            UUID userId,
            String email,
            String passwordHash,
            boolean alertConsent,
            String description
    ) {
        this.userId = userId;
        this.email = email;
        this.passwordHash = passwordHash;
        this.alertConsent = alertConsent;
        this.description = description;
    }

    public static UserJpaEntity create(
            UUID id,
            String email,
            String passwordHash,
            boolean alertConsent,
            String description
    ) {
        return new UserJpaEntity(
                id,
                email,
                passwordHash,
                alertConsent,
                description
        );
    }
}
