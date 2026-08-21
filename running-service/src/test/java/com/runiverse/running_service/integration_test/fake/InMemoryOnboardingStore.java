package com.runiverse.running_service.integration_test.fake;

import com.runiverse.running_service.application.user.exception.OnboardingNotCompletedException;
import com.runiverse.running_service.application.user.port.out.CheckNicknameDuplicatePort;
import com.runiverse.running_service.application.user.port.out.ExistsOnboardingPort;
import com.runiverse.running_service.application.user.port.out.LoadNicknamePort;
import com.runiverse.running_service.application.user.port.out.LoadOnboardingPort;
import com.runiverse.running_service.application.user.port.out.SaveOnboardingPort;
import com.runiverse.running_service.application.user.port.out.UpdateNicknamePort;
import com.runiverse.running_service.application.user.port.out.UpdateOnboardingPort;
import com.runiverse.running_service.domain.user.aggregate.UserOnboarding;
import com.runiverse.running_service.domain.user.vo.Nickname;
import com.runiverse.running_service.domain.common.vo.UserId;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public class InMemoryOnboardingStore implements ExistsOnboardingPort,
        CheckNicknameDuplicatePort, SaveOnboardingPort, LoadNicknamePort, UpdateNicknamePort,
        LoadOnboardingPort, UpdateOnboardingPort {

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

    // 실제 어댑터와 같이 닉네임은 nicknames가 단일 출처다 — 복원할 때 그 값을 얹는다
    @Override
    public Optional<UserOnboarding> loadOnboarding(UserId userId) {
        return Optional.ofNullable(onboardings.get(userId.value()))
                .map(onboarding -> new UserOnboarding(
                        onboarding.getUserId(),
                        nicknames.get(userId.value()).value(),
                        onboarding.getGender().name(),
                        onboarding.getBirthday().value(),
                        onboarding.getAvgPace().secondPerKm(),
                        onboarding.getWeight().value(),
                        onboarding.getHeight().value()
                ));
    }

    // 실제 어댑터는 닉네임·평균 페이스를 건드리지 않으므로 여기서도 기존 값을 지킨다
    @Override
    public void updateOnboarding(UserOnboarding onboarding) {
        UUID userId = onboarding.getUserId().value();
        if (!onboardings.containsKey(userId)) {
            throw new OnboardingNotCompletedException();
        }
        onboardings.put(userId, onboarding);
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
