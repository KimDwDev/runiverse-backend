package com.runiverse.running_service.application.user.port.out;

import com.runiverse.running_service.application.running.query.status.GetUserStatusResult;

// 유저 스트림으로 나가는 이벤트 하나 — 타입에 따라 채워지는 값이 다르다.
// 상태 계산은 running이 하고, 이 스트림은 받은 결과를 나르기만 한다
public record UserStreamEvent(UserStreamEventType type, GetUserStatusResult status) {

    // 연결 직후 스냅샷 — 2-2와 같은 판정 결과를 그대로 싣는다
    public static UserStreamEvent status(GetUserStatusResult status) {
        return new UserStreamEvent(UserStreamEventType.USER_STATUS, status);
    }
}
