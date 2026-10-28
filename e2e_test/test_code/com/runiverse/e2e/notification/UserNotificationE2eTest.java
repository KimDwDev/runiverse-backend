package com.runiverse.e2e.notification;

import com.runiverse.e2e.E2eTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// 조회 API(2-4)가 아직 없어 쌓인 행을 DB에서 직접 읽는다.
// 엔티티로 만든 실제 스키마(jsonb·CHECK·FK)를 통과하는지가 함께 확인된다
@DisplayName("배포 이미지 대상 인앱 알림 쌓기 E2E 테스트")
class UserNotificationE2eTest extends E2eTestSupport {

    @Test
    @DisplayName("친구 요청을 보내면 받는 사람에게만 요청한 사람을 담은 안 읽은 알림이 쌓인다")
    void friendRequestStoresNotificationForTarget() {
        // given
        TestUser requester = signUpAndOnboard();
        TestUser target = signUpAndOnboard();

        // when
        assertThat(post(friendRequestPath(target), null, requester.accessToken()).status()).isEqualTo(201);

        // then
        assertThat(notificationsOf(target)).containsExactly("FRIEND_REQUESTED|" + requester.userId() + "|false");
        assertThat(notificationsOf(requester)).isEmpty();
    }

    @Test
    @DisplayName("요청을 수락하면 요청했던 사람에게 수락한 사람을 담은 알림이 쌓인다")
    void acceptStoresNotificationForRequester() {
        // given
        TestUser requester = signUpAndOnboard();
        TestUser target = signUpAndOnboard();
        assertThat(post(friendRequestPath(target), null, requester.accessToken()).status()).isEqualTo(201);

        // when
        assertThat(put(friendRequestPath(requester), target.accessToken()).status()).isEqualTo(200);

        // then -> 받은 요청 알림은 사건의 기록이라 수락해도 그대로 남는다
        assertThat(notificationsOf(requester)).containsExactly("FRIEND_ACCEPTED|" + target.userId() + "|false");
        assertThat(notificationsOf(target)).containsExactly("FRIEND_REQUESTED|" + requester.userId() + "|false");

        // 응답을 못 받은 앱의 재요청은 이미 친구라 수락 알림을 또 쌓지 않는다
        assertThat(put(friendRequestPath(requester), target.accessToken()).status()).isEqualTo(200);
        assertThat(notificationsOf(requester)).hasSize(1);
    }

    @Test
    @DisplayName("서로 요청해 바로 친구가 되면 먼저 요청했던 사람에게 수락 알림이 쌓인다")
    void mutualRequestStoresAcceptedNotification() {
        // given
        TestUser first = signUpAndOnboard();
        TestUser second = signUpAndOnboard();
        assertThat(post(friendRequestPath(second), null, first.accessToken()).status()).isEqualTo(201);

        // when -> 상대가 먼저 요청해 뒀으니 새 요청이 아니라 수락이다(200)
        assertThat(post(friendRequestPath(first), null, second.accessToken()).status()).isEqualTo(200);

        // then
        assertThat(notificationsOf(first)).containsExactly("FRIEND_ACCEPTED|" + second.userId() + "|false");
        assertThat(notificationsOf(second)).containsExactly("FRIEND_REQUESTED|" + first.userId() + "|false");
    }

    @Test
    @DisplayName("요청이 실패하면 알림도 쌓이지 않는다")
    void failedRequestStoresNothing() {
        // given
        TestUser requester = signUpAndOnboard();
        TestUser target = signUpAndOnboard();
        assertThat(post(friendRequestPath(target), null, requester.accessToken()).status()).isEqualTo(201);

        // when -> 이미 요청한 사이라 409로 끝난다
        assertThat(post(friendRequestPath(target), null, requester.accessToken()).status()).isEqualTo(409);

        // then -> 첫 요청의 알림 하나뿐이다
        assertThat(notificationsOf(target)).hasSize(1);
    }

    private static String friendRequestPath(TestUser target) {
        return "/users/" + target.userId() + "/friend-request";
    }

    // 종류|일으킨 사람|읽음 — 종류마다 키가 달라 아는 키를 차례로 본다
    private List<String> notificationsOf(TestUser user) {
        return querySql("""
                SELECT type
                       || '|' || coalesce(payload ->> 'request_user_id', payload ->> 'accept_user_id')
                       || '|' || is_read
                FROM user_notifications
                WHERE user_id = '%s'
                ORDER BY user_notification_id;
                """.formatted(user.userId()));
    }
}
