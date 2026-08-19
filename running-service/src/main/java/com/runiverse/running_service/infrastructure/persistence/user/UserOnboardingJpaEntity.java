package com.runiverse.running_service.infrastructure.persistence.user;

import com.runiverse.running_service.domain.user.vo.Gender;
import com.runiverse.running_service.infrastructure.persistence.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Check;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Getter
@Entity
@Table(
        name = "user_onboardings",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_user_onboarding_nickname",
                columnNames = "nickname"
        )
)
@Check(name = "ck_user_onboarding_nickname", constraints = "char_length(nickname) between 2 and 16")
@Check(name = "ck_user_onboarding_gender", constraints = "gender in ('MALE', 'FEMALE')")
@Check(name = "ck_user_onboarding_avg_pace", constraints = "avg_pace between 120 and 1800")
@Check(name = "ck_user_onboarding_weight", constraints = "weight between 20.0 and 300.0")
@Check(name = "ck_user_onboarding_height", constraints = "height between 20.0 and 300.0")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserOnboardingJpaEntity extends BaseTimeEntity {

    @Id
    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "nickname", nullable = false, length = 16)
    private String nickname;

    @Enumerated(EnumType.STRING)
    @Column(name = "gender", nullable = false, length = 10)
    private Gender gender;

    @Column(name = "birthday", nullable = false)
    private LocalDate birthday;

    @Column(name = "avg_pace", nullable = false)
    private int avgPace;

    @Column(name = "weight", nullable = false, precision = 4, scale = 1)
    private BigDecimal weight;

    @Column(name = "height", nullable = false, precision = 4, scale = 1)
    private BigDecimal height;

    private UserOnboardingJpaEntity(UUID userId, String nickname, Gender gender, LocalDate birthday,
                                    int avgPace, BigDecimal weight, BigDecimal height) {
        this.userId = userId;
        this.nickname = nickname;
        this.gender = gender;
        this.birthday = birthday;
        this.avgPace = avgPace;
        this.weight = weight;
        this.height = height;
    }

    public static UserOnboardingJpaEntity create(UUID userId, String nickname, Gender gender,
                                                 LocalDate birthday, int avgPace,
                                                 BigDecimal weight, BigDecimal height) {
        return new UserOnboardingJpaEntity(userId, nickname, gender, birthday, avgPace, weight, height);
    }

    // 닉네임 변경
    public void changeNickname(String nickname) {
        this.nickname = nickname;
    }

    // FK 제약
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "user_id",
            insertable = false,
            updatable = false,
            foreignKey = @ForeignKey(name = "fk_user_onboard_users")
    )
    @OnDelete(action = OnDeleteAction.CASCADE)
    private UserJpaEntity user;
}
