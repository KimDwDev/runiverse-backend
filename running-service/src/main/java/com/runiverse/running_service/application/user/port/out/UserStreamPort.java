package com.runiverse.running_service.application.user.port.out;

import com.runiverse.running_service.domain.common.vo.UserId;

import java.util.Optional;

public interface UserStreamPort {

    // 등록하고, 밀려난 이전 연결은 돌려준다
    Optional<UserStreamConnection> register(UserId userId, UserStreamConnection connection);

    // 새 연결이 이미 자리를 가져갔으면 지우지 않는다 — 지웠으면 true
    boolean remove(UserId userId, UserStreamConnection connection);

    // 이 인스턴스에 붙어 있으면 돌려준다. 다른 서버에 붙은 유저는 그쪽이 자기 몫을 보낸다
    Optional<UserStreamConnection> find(UserId userId);
}
