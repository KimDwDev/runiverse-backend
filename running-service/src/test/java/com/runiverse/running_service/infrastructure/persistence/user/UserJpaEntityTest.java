package com.runiverse.running_service.infrastructure.persistence.user;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

public class UserJpaEntityTest {

    @Test
    @DisplayName("사용자 JPA 엔티티를 생성한다")
    void createUserJpaEntity() {
        // given
        UUID userId = UUID.randomUUID();

        // when
        UserJpaEntity entity = UserJpaEntity.create(
                userId,
                "test@example.com",
                "hashed-password",
                false,
                "러닝을 종료합니다"
        );


    }
}
