package com.runiverse.running_service.application.user.query.basicinfo;

import com.runiverse.running_service.application.user.exception.UserNotFoundException;
import com.runiverse.running_service.application.user.port.in.GetMyBasicInfoUsecase;
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
public class GetMyBasicInfoHandler implements GetMyBasicInfoUsecase {

    private final LoadUserByIdPort loadUserByIdPort;
    private final LoadNicknamePort loadNicknamePort;

    @Override
    public GetMyBasicInfoResult handle(GetMyBasicInfoQuery query) {
        UserId userId = new UserId(query.userId());

        // 1. 토큰이 가리키는 계정이 남아 있는지 확인
        User user = loadUserByIdPort.loadById(userId)
                .orElseThrow(UserNotFoundException::new);

        // 2. 닉네임은 온보딩에서 처음 생긴다 — 있으면 온보딩을 마친 것이다
        Optional<Nickname> nickname = loadNicknamePort.loadNickname(userId);

        return new GetMyBasicInfoResult(
                user.getUserId().value(),
                nickname.map(Nickname::value).orElse(null),
                nickname.isPresent()
        );
    }
}
