package com.runiverse.running_service.integration_test.fake;

import com.runiverse.running_service.application.running.port.out.LoadUserAvgPacePort;
import com.runiverse.running_service.application.user.exception.OnboardingNotCompletedException;
import com.runiverse.running_service.application.user.port.out.CheckNicknameDuplicatePort;
import com.runiverse.running_service.application.user.port.out.ExistsOnboardingPort;
import com.runiverse.running_service.application.user.port.out.LoadNicknamePort;
import com.runiverse.running_service.application.user.port.out.SaveOnboardingPort;
import com.runiverse.running_service.application.user.port.out.UpdateNicknamePort;
import com.runiverse.running_service.domain.running.metric.vo.Pace;
import com.runiverse.running_service.domain.user.aggregate.UserOnboarding;
import com.runiverse.running_service.domain.user.vo.AvgPace;
import com.runiverse.running_service.domain.user.vo.Nickname;
import com.runiverse.running_service.domain.common.vo.UserId;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

// 실제 UserPersistenceAdapter와 같이 러닝의 평균 페이스 조회도 이 저장소가 겸한다
// — 페이스 출처가 user_onboardings.avg_pace이기 때문이다
public class InMemoryOnboardingStore implements ExistsOnboardingPort,
        CheckNicknameDuplicatePort, SaveOnboardingPort, LoadNicknamePort, UpdateNicknamePort,
        LoadUserAvgPacePort {

    private final Map<UUID, UserOnboarding> onboardings = new LinkedHashMap<>();
    // 실제 어댑터가 user_onboardings.nickname 컬럼만 갱신하므로 닉네임은 따로 들고 있는다.
    // onboardings의 스냅샷이 아니라 이 맵이 현재 닉네임의 단일 출처다
    private final Map<UUID, Nickname> nicknames = new LinkedHashMap<>();
    // 온보딩 유즈케이스를 거치지 않고 상태만 심은 유저까지 포함한다
    private final Set<UUID> onboardedUserIds = new HashSet<>();

    @Override
    public void saveOnboarding(UserOnboarding onboarding) {
        UUID userId = onboarding.getUserId().value();
        onboardings.put(userId, onboarding);
        nicknames.put(userId, onboarding.getNickname());
        onboardedUserIds.add(userId);
    }

    @Override
    public boolean existsByUserId(UserId userId) {
        return onboardedUserIds.contains(userId.value());
    }

    @Override
    public boolean existsByNickname(Nickname nickname) {
        return nicknames.containsValue(nickname);
    }

    @Override
    public Optional<Nickname> loadNickname(UserId userId) {
        return Optional.ofNullable(nicknames.get(userId.value()));
    }

    // 실제 어댑터와 같이 온보딩 행이 없으면 막고, 유니크 위반은 DB가 아니라 여기서 흉내 낸다
    @Override
    public void updateNickname(UserId userId, Nickname nickname) {
        if (!nicknames.containsKey(userId.value())) {
            throw new OnboardingNotCompletedException();
        }
        nicknames.put(userId.value(), nickname);
    }

    // 온보딩 행이 없으면 빈 Optional — 실제 어댑터와 같이 "온보딩 미완료"가 이걸로 판정된다
    @Override
    public Optional<Pace> loadAvgPace(UserId userId) {
        return Optional.ofNullable(onboardings.get(userId.value()))
                .map(UserOnboarding::getAvgPace)
                .map(AvgPace::secondPerKm)
                .map(Pace::new);
    }

    // 테스트 준비
    public void markOnboarded(UUID userId) {
        onboardedUserIds.add(userId);
    }

    // 검증 전용 — 닉네임은 갱신되지 않는 온보딩 시점 스냅샷이다. 현재 닉네임은 nicknameOf로 본다
    public Optional<UserOnboarding> findByUserId(UUID userId) {
        return Optional.ofNullable(onboardings.get(userId));
    }

    // 검증 전용 — 갱신까지 반영된 현재 닉네임
    public Optional<String> nicknameOf(UUID userId) {
        return Optional.ofNullable(nicknames.get(userId)).map(Nickname::value);
    }

    public int size() {
        return onboardings.size();
    }
}
