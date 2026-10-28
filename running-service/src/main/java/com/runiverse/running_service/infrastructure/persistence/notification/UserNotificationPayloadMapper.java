package com.runiverse.running_service.infrastructure.persistence.notification;

import com.runiverse.running_service.domain.notification.vo.FriendAcceptedPayload;
import com.runiverse.running_service.domain.notification.vo.FriendRequestedPayload;
import com.runiverse.running_service.domain.notification.vo.RunningInvitedPayload;
import com.runiverse.running_service.domain.notification.vo.UserNotificationPayload;

import java.util.Map;

// 도메인 payload ↔ jsonb의 키 이름을 정하는 유일한 곳이다.
// 키는 저장된 약속이다 — 보관 기간 동안 옛 행이 남으므로 이름을 바꾸지 않는다
final class UserNotificationPayloadMapper {

    static final String REQUEST_USER_ID = "request_user_id";
    static final String ACCEPT_USER_ID = "accept_user_id";
    static final String RUNNING_ROOM_ID = "running_room_id";

    private UserNotificationPayloadMapper() {
    }

    // 종류가 늘면 sealed라 여기서 컴파일 에러가 난다 — 키를 빠뜨린 채 저장되지 않는다
    static Map<String, Object> toJson(UserNotificationPayload payload) {
        return switch (payload) {
            case FriendRequestedPayload p -> Map.of(REQUEST_USER_ID, p.requestUserId().value().toString());
            case FriendAcceptedPayload p -> Map.of(ACCEPT_USER_ID, p.acceptUserId().value().toString());
            // 방 ID는 숫자로 둔다 — 초대 상태를 찾는 running_room_invitations의 bigint와 같은 값이다
            case RunningInvitedPayload p -> Map.of(
                    REQUEST_USER_ID, p.requestUserId().value().toString(),
                    RUNNING_ROOM_ID, p.runningRoomId().value());
        };
    }
}
