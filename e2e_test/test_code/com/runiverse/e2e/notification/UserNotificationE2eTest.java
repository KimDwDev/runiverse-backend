package com.runiverse.e2e.notification;

import com.runiverse.e2e.E2eTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

// 조회 API(2-4)가 아직 없어 쌓인 행을 DB에서 직접 읽는다.
// 엔티티로 만든 실제 스키마(jsonb·CHECK·FK)를 통과하는지가 함께 확인된다
@DisplayName("배포 이미지 대상 인앱 알림 쌓기 E2E 테스트")
class UserNotificationE2eTest extends E2eTestSupport {

    private static final ZoneId APP_ZONE = ZoneId.of("Asia/Seoul");   // 호스트 시간대로 만들면 시작 시각이 어긋난다

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

    @Test
    @DisplayName("친구를 초대하면 받은 사람에게 초대한 사람과 방을 담은 알림이 쌓이고, 다시 초대하면 하나 더 쌓인다")
    void invitationStoresNotificationPerSend() {
        // given
        TestUser host = signUpAndOnboard();
        TestUser friend = signUpAndOnboard();
        assertThat(post(friendRequestPath(friend), null, host.accessToken()).status()).isEqualTo(201);
        assertThat(put(friendRequestPath(host), friend.accessToken()).status()).isEqualTo(200);
        int roomId = openInviteRoom(host);

        try {
            // when -> 대기 중인 초대를 다시 보내도 된다
            assertThat(invite(host, roomId, friend).status()).isEqualTo(204);
            assertThat(invite(host, roomId, friend).status()).isEqualTo(204);

            // then -> 초대 행은 하나로 횟수만 오르지만 알림은 보낼 때마다 쌓인다
            assertThat(notificationsOf(friend)).containsExactly(
                    "FRIEND_REQUESTED|" + host.userId() + "|false",
                    "RUNNING_INVITED|" + host.userId() + "|false",
                    "RUNNING_INVITED|" + host.userId() + "|false");
            assertThat(querySql("""
                    SELECT DISTINCT payload ->> 'running_room_id'
                    FROM user_notifications
                    WHERE user_id = '%s' AND type = 'RUNNING_INVITED';
                    """.formatted(friend.userId()))).containsExactly(String.valueOf(roomId));
        } finally {
            assertThat(post("/match-rooms/leave", null, host.accessToken()).status()).isEqualTo(204);
        }
    }

    @Test
    @DisplayName("친구가 아닌 사람에게는 초대가 거절되고 알림도 쌓이지 않는다")
    void rejectedInvitationStoresNothing() {
        // given
        TestUser host = signUpAndOnboard();
        TestUser stranger = signUpAndOnboard();
        int roomId = openInviteRoom(host);

        try {
            // when
            Response rejected = invite(host, roomId, stranger);

            // then
            assertThat(rejected.status()).isBetween(400, 499);
            assertThat(notificationsOf(stranger)).isEmpty();
        } finally {
            assertThat(post("/match-rooms/leave", null, host.accessToken()).status()).isEqualTo(204);
        }
    }

    private int openInviteRoom(TestUser host) {
        LocalDateTime now = LocalDateTime.now(APP_ZONE);
        LocalDateTime start = now.plusMinutes(30).truncatedTo(ChronoUnit.MINUTES);
        assumeTrue(!start.isAfter(now.toLocalDate().atTime(23, 59)),
                "KST 23:29 이후에는 오늘 안에 고를 시작 시각이 부족하다");
        Response opened = post("/match-rooms/invite", Map.of(
                "scheduledStartAt", start.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                "targetDistanceMeters", 7_000), host.accessToken());
        assertThat(opened.status()).isEqualTo(201);
        return opened.number("runningRoomId");
    }

    private Response invite(TestUser inviter, int roomId, TestUser invitee) {
        return post("/match-rooms/" + roomId + "/invitations", Map.of("inviteeId", invitee.userId()),
                inviter.accessToken());
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
