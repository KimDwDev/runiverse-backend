package com.runiverse.running_service.domain.user.aggregate;

import com.runiverse.running_service.domain.common.exception.UserIdRequiredException;
import com.runiverse.running_service.domain.user.vo.AvgPace;
import com.runiverse.running_service.domain.user.vo.Birthday;
import com.runiverse.running_service.domain.user.vo.Gender;
import com.runiverse.running_service.domain.user.vo.Height;
import com.runiverse.running_service.domain.user.vo.Nickname;
import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.domain.user.vo.Weight;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

@Getter
public class UserOnboarding {

    private final UserId userId;
    private final Nickname nickname;
    private final Gender gender;
    private final Birthday birthday;
    private final AvgPace avgPace;
    private final Weight weight;
    private final Height height;

    // 온보딩 완료 — 저장된 값을 다시 도메인으로 되살릴 때도 쓴다(User와 같은 방식)
    public UserOnboarding(UserId userId, String nickname, String gender, LocalDate birthday,
                          int avgPace, BigDecimal weight, BigDecimal height) {
        this(userId,
                new Nickname(nickname),
                Gender.from(gender),
                new Birthday(birthday),
                new AvgPace(avgPace),
                new Weight(weight),
                new Height(height));
    }

    // 내부 전용 - 수정 시 기존 VO를 넘기기 위해 필요
    private UserOnboarding(UserId userId, Nickname nickname, Gender gender, Birthday birthday,
                           AvgPace avgPace, Weight weight, Height height) {
        if (userId == null) {
            throw new UserIdRequiredException();
        }
        this.userId = userId;
        this.nickname = nickname;
        this.gender = gender;
        this.birthday = birthday;
        this.avgPace = avgPace;
        this.weight = weight;
        this.height = height;
    }

    // 프로필 수정 — null인 값은 그대로 둔다.
    // User를 거치지 않고 직접 부를 수 있다. 온보딩은 별도 테이블이고 조회·저장 포트도 따로라
    // User가 반쪽만 복원되는 지금 구조에서는 User.updateOnboarding으로 닿을 수 없다
    public UserOnboarding change(String nickname, String gender, LocalDate birthday,
                                 Integer avgPace, BigDecimal weight, BigDecimal height) {
        return new UserOnboarding(
                userId,
                nickname != null ? new Nickname(nickname) : this.nickname,
                gender != null ? Gender.from(gender) : this.gender,
                birthday != null ? new Birthday(birthday) : this.birthday,
                avgPace != null ? new AvgPace(avgPace) : this.avgPace,
                weight != null ? new Weight(weight) : this.weight,
                height != null ? new Height(height) : this.height
        );
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof UserOnboarding other)) {
            return false;
        }
        return userId.equals(other.userId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId);
    }
}
