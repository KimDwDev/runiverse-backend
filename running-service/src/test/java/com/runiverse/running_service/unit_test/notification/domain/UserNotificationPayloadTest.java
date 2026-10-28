package com.runiverse.running_service.unit_test.notification.domain;

import com.github.f4b6a3.uuid.UuidCreator;
import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.domain.notification.exception.UserNotificationActorRequiredException;
import com.runiverse.running_service.domain.notification.vo.FriendAcceptedPayload;
import com.runiverse.running_service.domain.notification.vo.FriendRequestedPayload;
import com.runiverse.running_service.domain.notification.vo.UserNotificationType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("알림 payload 단위 테스트")
class UserNotificationPayloadTest {

    private static final UserId ACTOR_ID = new UserId(UuidCreator.getTimeOrderedEpoch());

    @Test
    @DisplayName("친구 요청 payload는 요청한 사람을 일으킨 사람으로 갖고 FRIEND_REQUESTED다")
    void friendRequestedCarriesRequester() {
        // when
        FriendRequestedPayload payload = new FriendRequestedPayload(ACTOR_ID);

        // then -> type은 구현체가 정해 어긋날 수 없다
        assertThat(payload.type()).isEqualTo(UserNotificationType.FRIEND_REQUESTED);
        assertThat(payload.actorId()).isEqualTo(ACTOR_ID);
    }

    @Test
    @DisplayName("친구 수락 payload는 수락한 사람을 일으킨 사람으로 갖고 FRIEND_ACCEPTED다")
    void friendAcceptedCarriesAccepter() {
        // when
        FriendAcceptedPayload payload = new FriendAcceptedPayload(ACTOR_ID);

        // then
        assertThat(payload.type()).isEqualTo(UserNotificationType.FRIEND_ACCEPTED);
        assertThat(payload.actorId()).isEqualTo(ACTOR_ID);
    }

    @Test
    @DisplayName("요청한 사람이 없으면 친구 요청 payload를 만들 수 없다")
    void rejectsFriendRequestedWithoutRequester() {
        // when & then -> DB의 깨진 JSON을 되살릴 때도 이 생성자를 거쳐 걸러진다
        assertThatThrownBy(() -> new FriendRequestedPayload(null))
                .isInstanceOf(UserNotificationActorRequiredException.class);
    }

    @Test
    @DisplayName("수락한 사람이 없으면 친구 수락 payload를 만들 수 없다")
    void rejectsFriendAcceptedWithoutAccepter() {
        // when & then
        assertThatThrownBy(() -> new FriendAcceptedPayload(null))
                .isInstanceOf(UserNotificationActorRequiredException.class);
    }
}
