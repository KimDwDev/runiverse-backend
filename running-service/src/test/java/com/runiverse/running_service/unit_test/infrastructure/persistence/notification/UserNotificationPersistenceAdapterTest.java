package com.runiverse.running_service.unit_test.infrastructure.persistence.notification;

import com.github.f4b6a3.uuid.UuidCreator;
import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.domain.notification.UserNotification;
import com.runiverse.running_service.domain.notification.vo.FriendAcceptedPayload;
import com.runiverse.running_service.domain.notification.vo.FriendRequestedPayload;
import com.runiverse.running_service.domain.notification.vo.RunningInvitedPayload;
import com.runiverse.running_service.domain.notification.vo.UserNotificationType;
import com.runiverse.running_service.domain.running.room.vo.RunningRoomId;
import com.runiverse.running_service.infrastructure.persistence.notification.UserNotificationJpaEntity;
import com.runiverse.running_service.infrastructure.persistence.notification.UserNotificationPersistenceAdapter;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

// jsonb 매핑·CHECK·FK는 실제 DB에서만 확인된다 — 여기서는 어떤 키와 값으로 저장하는지만 본다
@ExtendWith(MockitoExtension.class)
@DisplayName("알림 저장 어댑터 단위 테스트")
class UserNotificationPersistenceAdapterTest {

    private static final UUID RECIPIENT_ID = UuidCreator.getTimeOrderedEpoch();
    private static final UUID ACTOR_ID = UuidCreator.getTimeOrderedEpoch();

    @Mock
    private EntityManager entityManager;

    @InjectMocks
    private UserNotificationPersistenceAdapter adapter;

    private UserNotificationJpaEntity persisted() {
        ArgumentCaptor<UserNotificationJpaEntity> captor = ArgumentCaptor.forClass(UserNotificationJpaEntity.class);
        verify(entityManager).persist(captor.capture());
        return captor.getValue();
    }

    @Test
    @DisplayName("친구 요청 알림은 request_user_id 키에 요청한 사람을 문자열로 담아 안 읽음으로 쌓는다")
    void savesFriendRequested() {
        // when
        adapter.save(UserNotification.of(new UserId(RECIPIENT_ID), new FriendRequestedPayload(new UserId(ACTOR_ID))));

        // then -> 키 이름은 저장된 약속이다
        UserNotificationJpaEntity entity = persisted();
        assertThat(entity.getUserId()).isEqualTo(RECIPIENT_ID);
        assertThat(entity.getType()).isEqualTo(UserNotificationType.FRIEND_REQUESTED);
        assertThat(entity.getPayload()).isEqualTo(Map.of("request_user_id", ACTOR_ID.toString()));
        assertThat(entity.isRead()).isFalse();
    }

    @Test
    @DisplayName("친구 수락 알림은 accept_user_id 키에 수락한 사람을 담는다")
    void savesFriendAccepted() {
        // when
        adapter.save(UserNotification.of(new UserId(RECIPIENT_ID), new FriendAcceptedPayload(new UserId(ACTOR_ID))));

        // then
        UserNotificationJpaEntity entity = persisted();
        assertThat(entity.getType()).isEqualTo(UserNotificationType.FRIEND_ACCEPTED);
        assertThat(entity.getPayload()).isEqualTo(Map.of("accept_user_id", ACTOR_ID.toString()));
    }

    @Test
    @DisplayName("초대 알림은 request_user_id에 초대한 사람, running_room_id에 방 ID를 숫자로 담는다")
    void savesRunningInvited() {
        // when
        adapter.save(UserNotification.of(new UserId(RECIPIENT_ID),
                new RunningInvitedPayload(new UserId(ACTOR_ID), new RunningRoomId(130L))));

        // then -> 방 ID는 running_room_invitations의 bigint와 맞춰 숫자다
        UserNotificationJpaEntity entity = persisted();
        assertThat(entity.getType()).isEqualTo(UserNotificationType.RUNNING_INVITED);
        assertThat(entity.getPayload()).isEqualTo(Map.of(
                "request_user_id", ACTOR_ID.toString(),
                "running_room_id", 130L));
    }
}
