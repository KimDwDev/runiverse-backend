package com.runiverse.running_service.application.user.query.profile;

import com.runiverse.running_service.application.user.exception.UserNotFoundException;
import com.runiverse.running_service.application.user.port.in.GetMyProfileUsecase;
import com.runiverse.running_service.application.user.port.out.LoadOnboardingProfilePort;
import com.runiverse.running_service.application.user.port.out.LoadUserByIdPort;
import com.runiverse.running_service.application.user.port.out.OnboardingProfile;
import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.domain.user.aggregate.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GetMyProfileHandler implements GetMyProfileUsecase {

    private final LoadUserByIdPort loadUserByIdPort;
    private final LoadOnboardingProfilePort loadOnboardingProfilePort;

    @Override
    public GetMyProfileResult handle(GetMyProfileQuery query) {
        UserId userId = new UserId(query.userId());

        // 1. 토큰이 가리키는 계정이 남아 있는지 확인
        User user = loadUserByIdPort.loadById(userId).orElseThrow(UserNotFoundException::new);

        // 2. 온보딩 전이면 소개글만 채워 답한다 — 편집 화면은 온보딩 전에도 열린다
        Optional<OnboardingProfile> onboarding = loadOnboardingProfilePort.loadOnboardingProfile(userId);

        return new GetMyProfileResult(
                user.getIntroduction().value(),
                onboarding.map(profile -> profile.gender().name()).orElse(null),
                onboarding.map(profile -> profile.birthday().value()).orElse(null),
                onboarding.map(profile -> profile.weight().value()).orElse(null),
                onboarding.map(profile -> profile.height().value()).orElse(null)
        );
    }
}
