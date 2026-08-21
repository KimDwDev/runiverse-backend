package com.runiverse.running_service.application.user.query.profile;

import com.runiverse.running_service.application.user.exception.UserNotFoundException;
import com.runiverse.running_service.application.user.port.in.GetProfileUsecase;
import com.runiverse.running_service.application.user.port.out.LoadNicknamePort;
import com.runiverse.running_service.application.user.port.out.LoadUserByIdPort;
import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.domain.user.aggregate.User;
import com.runiverse.running_service.domain.user.vo.Nickname;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GetProfileHandler implements GetProfileUsecase {

    private final LoadUserByIdPort loadUserByIdPort;
    private final LoadNicknamePort loadNicknamePort;

    @Override
    public GetProfileResult handle(GetProfileQuery query) {
        UserId userId = new UserId(query.userId());

        // 1. 토큰이 가리키는 계정이 남아 있는지 확인
        User user = loadUserByIdPort.loadById(userId)
                .orElseThrow(UserNotFoundException::new);

        // 2. 닉네임은 온보딩에서 처음 생긴다 — 있으면 온보딩을 마친 것이다
        Optional<Nickname> nickname = loadNicknamePort.loadNickname(userId);

        return new GetProfileResult(
                user.getUserId().value(),
                nickname.map(Nickname::value).orElse(null),
                nickname.isPresent()
        );
    }
}
