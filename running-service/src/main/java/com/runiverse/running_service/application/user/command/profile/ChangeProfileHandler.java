package com.runiverse.running_service.application.user.command.profile;

import com.runiverse.running_service.application.user.exception.OnboardingNotCompletedException;
import com.runiverse.running_service.application.user.exception.UserNotFoundException;
import com.runiverse.running_service.application.user.port.in.ChangeProfileUsecase;
import com.runiverse.running_service.application.user.port.out.LoadOnboardingPort;
import com.runiverse.running_service.application.user.port.out.LoadUserByIdPort;
import com.runiverse.running_service.application.user.port.out.UpdateIntroductionPort;
import com.runiverse.running_service.application.user.port.out.UpdateOnboardingPort;
import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.domain.user.aggregate.UserOnboarding;
import com.runiverse.running_service.domain.user.vo.Introduction;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

@Service
@RequiredArgsConstructor
@Transactional
public class ChangeProfileHandler implements ChangeProfileUsecase {

    private final LoadUserByIdPort loadUserByIdPort;
    private final UpdateIntroductionPort updateIntroductionPort;
    private final LoadOnboardingPort loadOnboardingPort;
    private final UpdateOnboardingPort updateOnboardingPort;

    @Override
    public ChangeProfileResult handle(ChangeProfileCommand command) {
        UserId userId = new UserId(command.userId());

        // 1. 갱신 대상이 남아 있는지 확인
        loadUserByIdPort.loadById(userId).orElseThrow(UserNotFoundException::new);

        // 2. 온보딩 값이 담겼으면 저장 전에 완료 여부부터 막는다 — 절반만 반영되지 않도록
        UserOnboarding onboarding = null;
        if (command.hasOnboardingField()) {
            onboarding = loadOnboardingPort.loadOnboarding(userId)
                    .orElseThrow(OnboardingNotCompletedException::new);
        }

        // 3. 소개글은 users에 있어 온보딩 전에도 바꿀 수 있다
        String introduction = null;
        if (command.introduction() != null) {
            Introduction changed = new Introduction(command.introduction());
            updateIntroductionPort.updateIntroduction(userId, changed);
            introduction = changed.value();
        }

        // 4. 닉네임은 11-6이, 평균 페이스는 러닝 기록이 갱신하므로 넘기지 않는다
        String gender = null;
        LocalDate birthday = null;
        BigDecimal weightKg = null;
        BigDecimal heightCm = null;
        if (onboarding != null) {
            UserOnboarding changed = onboarding.change(null, command.gender(), command.birthday(),
                    null, command.weightKg(), command.heightCm());
            updateOnboardingPort.updateOnboarding(changed);

            // 갱신본은 안 보낸 값도 들고 있다 — 요청에 담긴 것만 골라 돌려준다
            gender = command.gender() == null ? null : changed.getGender().name();
            birthday = command.birthday() == null ? null : changed.getBirthday().value();
            weightKg = command.weightKg() == null ? null : changed.getWeight().value();
            heightCm = command.heightCm() == null ? null : changed.getHeight().value();
        }

        return new ChangeProfileResult(introduction, gender, birthday, weightKg, heightCm);
    }
}
