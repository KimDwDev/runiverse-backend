package com.runiverse.running_service.infrastructure.persistence.user;

import com.runiverse.running_service.domain.user.vo.Gender;
import com.runiverse.running_service.domain.user.vo.LoginType;
import com.runiverse.running_service.infrastructure.persistence.common.BaseCreatedAtEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Entity
@Table(
        name = "delete_users",
        // 보관 기간 만료 배치가 탄다 — email이 남은 행만 훑어 처리 끝난 대다수를 배제한다
        indexes = @Index(name = "idx_delete_user_pending", columnList = "email, created_at")
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DeleteUserJpaEntity extends BaseCreatedAtEntity {

    // 논리 참조 — users를 하드 삭제한 뒤에도 값이 남아야 해서 FK를 걸지 않는다
    @Id
    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "email", length = 255)
    private String email;

    @Column(name = "nickname", length = 16)
    private String nickname;

    @Enumerated(EnumType.STRING)
    @Column(name = "gender", length = 10)
    private Gender gender;

    @Column(name = "birth_year")
    private Integer birthYear;

    @Column(name = "avg_pace")
    private Integer avgPace;

    @Column(name = "bmi", precision = 5, scale = 1)
    private BigDecimal bmi;

    @Enumerated(EnumType.STRING)
    @Column(name = "login_type", nullable = false, length = 10)
    private LoginType loginType;

    // users.created_at이다 — 온보딩 완료 시각이 아니다
    @Column(name = "joined_at", nullable = false)
    private LocalDateTime joinedAt;

    private DeleteUserJpaEntity(UUID userId, String email, String nickname, Gender gender,
                                Integer birthYear, Integer avgPace, BigDecimal bmi,
                                LoginType loginType, LocalDateTime joinedAt) {
        this.userId = userId;
        this.email = email;
        this.nickname = nickname;
        this.gender = gender;
        this.birthYear = birthYear;
        this.avgPace = avgPace;
        this.bmi = bmi;
        this.loginType = loginType;
        this.joinedAt = joinedAt;
    }

    public static DeleteUserJpaEntity create(UUID userId, String email, String nickname,
                                             Gender gender, Integer birthYear, Integer avgPace,
                                             BigDecimal bmi, LoginType loginType,
                                             LocalDateTime joinedAt) {
        return new DeleteUserJpaEntity(userId, email, nickname, gender,
                birthYear, avgPace, bmi, loginType, joinedAt);
    }
}
