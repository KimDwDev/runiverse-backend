package com.runiverse.e2e.invite;

import com.runiverse.e2e.E2eTestSupport;
import com.runiverse.e2e.MatchStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

// 초대 행 조회·저장(running_room_invitations)과 친구·활성 신청 일괄 조회 JPQL을 실제 DB에서 탄다
@DisplayName("배포 이미지 대상 러닝 초대·수락·거절 E2E 테스트")
class InvitationE2eTest extends E2eTestSupport {

    private static final ZoneId APP_ZONE = ZoneId.of("Asia/Seoul");
    private static final int DISTANCE = 7_000;

    private static LocalDateTime selectableStartOrSkip() {
        LocalDateTime now = LocalDateTime.now(APP_ZONE);
        LocalDateTime start = now.plusMinutes(30).truncatedTo(ChronoUnit.MINUTES);
        assumeTrue(!start.isAfter(now.toLocalDate().atTime(23, 59)),
                "KST 23:29 이후에는 오늘 안에 고를 시작 시각이 부족하다");
        return start;
    }

    private int openRoom(TestUser host) {
        Response opened = post("/match-rooms/invite", Map.of(
                "scheduledStartAt", selectableStartOrSkip().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                "targetDistanceMeters", DISTANCE), host.accessToken());
        assertThat(opened.status()).isEqualTo(201);
        return opened.number("runningRoomId");
    }

    // 요청 → 수락으로 친구를 맺는다
    private void befriend(TestUser a, TestUser b) {
        assertThat(post("/users/" + b.userId() + "/friend-request", null, a.accessToken()).status())
                .isBetween(200, 299);
        assertThat(put("/users/" + a.userId() + "/friend-request", b.accessToken()).status())
                .isBetween(200, 299);
    }

    private Response invite(TestUser inviter, int roomId, TestUser... invitees) {
        return post("/match-rooms/" + roomId + "/invitations",
                Map.of("inviteeIds", Arrays.stream(invitees).map(TestUser::userId).toList()),
                inviter.accessToken());
    }

    private Response accept(TestUser invitee, int roomId) {
        return post("/match-rooms/" + roomId + "/invitations/accept", null, invitee.accessToken());
    }

    private Response decline(TestUser invitee, int roomId) {
        return post("/match-rooms/" + roomId + "/invitations/decline", null, invitee.accessToken());
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> listOf(Map<String, Object> body, String field) {
        return (List<Map<String, Object>>) body.get(field);
    }

    @Test
    @DisplayName("초대 → 거절 → 다시 초대 → 수락 → 나가기가 스트림과 DB에 이어서 반영된다")
    void inviteDeclineReinviteAcceptLeave() {
        // given
        TestUser host = signUpAndOnboard();
        TestUser friend = signUpAndOnboard();
        befriend(host, friend);
        int roomId = openRoom(host);

        try (MatchStream hostStream = openMatchStream(host.accessToken())) {
            assertThat(hostStream.status()).isEqualTo(200);
            hostStream.await("INVITE_ROOM_ENTRANCE");

            // when - 1. 초대 — 초대는 신청이 아니라 받은 사람은 여전히 IDLE이다
            Response sent = invite(host, roomId, friend);
            assertThat(sent.status()).isEqualTo(201);
            assertThat(sent.objects("invitations")).singleElement().satisfies(invitation -> {
                assertThat(invitation.get("userId")).isEqualTo(friend.userId());
                assertThat(invitation.get("status")).isEqualTo("PENDING");
                assertThat(((Number) invitation.get("inviteCount")).intValue()).isEqualTo(1);
            });
            assertThat(listOf(hostStream.await("INVITE_ROOM_UPDATED"), "invitations"))
                    .extracting(invitee -> invitee.get("status")).containsExactly("PENDING");
            assertThat(get("/users/me/status", friend.accessToken()).text("status")).isEqualTo("IDLE");

            // 2. 거절 — 행이 남아 현황에 거절로 보인다. 다시 거절하면 409다
            assertThat(decline(friend, roomId).status()).isEqualTo(204);
            assertThat(listOf(hostStream.await("INVITE_ROOM_UPDATED"), "invitations"))
                    .extracting(invitee -> invitee.get("status")).containsExactly("DECLINED");
            Response declinedAgain = decline(friend, roomId);
            assertThat(declinedAgain.status()).isEqualTo(409);
            assertThat(declinedAgain.text("code")).isEqualTo("RUNNING_INVITATION_NOT_PENDING");
            assertThat(accept(friend, roomId).text("code")).isEqualTo("RUNNING_INVITATION_NOT_PENDING");

            // 3. 다시 초대 — 같은 행이 대기로 되살아나고 횟수는 이어서 센다
            Response resent = invite(host, roomId, friend);
            assertThat(((Number) resent.objects("invitations").getFirst().get("inviteCount")).intValue())
                    .isEqualTo(2);
            hostStream.await("INVITE_ROOM_UPDATED");

            // 4. 수락 — 신청·세션이 생겨 참가자로 옮겨 가고 현황에서 빠진다
            Response accepted = accept(friend, roomId);
            assertThat(accepted.status()).isEqualTo(201);
            assertThat(accepted.number("runningRoomId")).isEqualTo(roomId);
            Map<String, Object> afterAccept = hostStream.await("INVITE_ROOM_UPDATED");
            assertThat(listOf(afterAccept, "players")).extracting(player -> player.get("userId"))
                    .containsExactlyInAnyOrder(host.userId(), friend.userId());
            assertThat(listOf(afterAccept, "invitations")).isEmpty();
            assertThat(get("/users/me/status", friend.accessToken()).text("status")).isEqualTo("WAITING");

            // 수락한 사람의 스트림은 초대 방 입장 스냅샷부터 받는다
            try (MatchStream friendStream = openMatchStream(friend.accessToken())) {
                Map<String, Object> entrance = friendStream.await("INVITE_ROOM_ENTRANCE");
                assertThat(((Number) entrance.get("runningRoomId")).intValue()).isEqualTo(roomId);
                assertThat(((Number) entrance.get("startsInMs")).longValue()).isPositive();
            }

            // 5. 나가기 — 매칭과 같은 경로다. 현황에 나감으로 돌아온다
            assertThat(post("/match-rooms/leave", null, friend.accessToken()).status()).isEqualTo(204);
            Map<String, Object> afterLeave = hostStream.await("INVITE_ROOM_UPDATED");
            assertThat(listOf(afterLeave, "players")).extracting(player -> player.get("userId"))
                    .containsExactly(host.userId());
            assertThat(listOf(afterLeave, "invitations")).extracting(invitee -> invitee.get("status"))
                    .containsExactly("LEFT");
        }
        assertThat(post("/match-rooms/leave", null, host.accessToken()).status()).isEqualTo(204);
    }

    @Test
    @DisplayName("초대받지 않은 사람은 방 ID로 들어갈 수 없고, 친구가 아닌 사람에게는 보낼 수 없다")
    void strangerIsRejected() {
        // given
        TestUser host = signUpAndOnboard();
        TestUser stranger = signUpAndOnboard();
        int roomId = openRoom(host);

        // when & then - 1. 받은 초대가 없으면 404다
        Response sneaked = accept(stranger, roomId);
        assertThat(sneaked.status()).isEqualTo(404);
        assertThat(sneaked.text("code")).isEqualTo("NOT_FOUND");
        assertThat(get("/users/me/status", stranger.accessToken()).text("status")).isEqualTo("IDLE");

        // 2. 친구가 아니면 409이고 아무에게도 가지 않는다
        Response notFriend = invite(host, roomId, stranger);
        assertThat(notFriend.status()).isEqualTo(409);
        assertThat(notFriend.text("code")).isEqualTo("NOT_FRIEND");
        assertThat(accept(stranger, roomId).status()).isEqualTo(404);

        // 3. 참가자가 아닌 사람은 초대할 수 없다 — 방이 있는지도 알려 주지 않는다
        assertThat(invite(stranger, roomId, host).status()).isEqualTo(404);

        assertThat(post("/match-rooms/leave", null, host.accessToken()).status()).isEqualTo(204);
    }

    @Test
    @DisplayName("다른 활성 신청이 있는 친구는 건너뛰고, 초대 뒤에 신청이 생기면 수락이 막힌다")
    void activeApplicationBlocksInvitation() {
        // given
        TestUser host = signUpAndOnboard();
        TestUser busy = signUpAndOnboard();
        TestUser later = signUpAndOnboard();
        befriend(host, busy);
        befriend(host, later);
        int roomId = openRoom(host);
        int busyRoom = openRoom(busy);

        // when - 1. 활성 신청이 있는 친구는 응답에서 빠진다 — 오류가 아니다
        Response sent = invite(host, roomId, busy, later);
        assertThat(sent.status()).isEqualTo(201);
        assertThat(sent.objects("invitations")).extracting(invitation -> invitation.get("userId"))
                .containsExactly(later.userId());

        // 2. 초대받은 뒤 자기 방을 열었다 — 수락이 막히고 초대는 대기로 남는다
        openRoom(later);
        Response blocked = accept(later, roomId);
        assertThat(blocked.status()).isEqualTo(409);
        assertThat(blocked.text("code")).isEqualTo("RUNNING_ALREADY_IN_PROGRESS");

        // 3. 자기 방을 나가면 다시 수락할 수 있다
        assertThat(post("/match-rooms/leave", null, later.accessToken()).status()).isEqualTo(204);
        assertThat(accept(later, roomId).status()).isEqualTo(201);

        assertThat(busyRoom).isNotEqualTo(roomId);
        assertThat(post("/match-rooms/leave", null, busy.accessToken()).status()).isEqualTo(204);
        assertThat(post("/match-rooms/leave", null, later.accessToken()).status()).isEqualTo(204);
        assertThat(post("/match-rooms/leave", null, host.accessToken()).status()).isEqualTo(204);
    }

    @Test
    @DisplayName("혼자인 방에서 만든 사람이 나가면 대기 중이던 초대가 만료돼 수락할 수 없다")
    void lastLeaveExpiresInvitation() {
        // given
        TestUser host = signUpAndOnboard();
        TestUser friend = signUpAndOnboard();
        befriend(host, friend);
        int roomId = openRoom(host);
        assertThat(invite(host, roomId, friend).status()).isEqualTo(201);

        // when -> 방이 취소된다
        assertThat(post("/match-rooms/leave", null, host.accessToken()).status()).isEqualTo(204);

        // then -> 초대 행이 EXPIRED로 저장됐다 — 수락은 방이 닫힌 것, 거절은 대기 초대가 아닌 것으로 막힌다
        Response accepted = accept(friend, roomId);
        assertThat(accepted.status()).isEqualTo(409);
        assertThat(accepted.text("code")).isEqualTo("RUNNING_INVITATION_CLOSED");
        Response declined = decline(friend, roomId);
        assertThat(declined.status()).isEqualTo(409);
        assertThat(declined.text("code")).isEqualTo("RUNNING_INVITATION_NOT_PENDING");
        assertThat(get("/users/me/status", friend.accessToken()).text("status")).isEqualTo("IDLE");
    }

    @Test
    @DisplayName("빈 초대 목록은 Request 검증 문구로 400이다")
    void emptyInviteesIsBadRequest() {
        // given
        TestUser host = signUpAndOnboard();
        int roomId = openRoom(host);

        // when
        Response response = post("/match-rooms/" + roomId + "/invitations",
                Map.of("inviteeIds", List.of()), host.accessToken());

        // then
        assertThat(response.status()).isEqualTo(400);
        assertThat(response.text("code")).isEqualTo("INVALID_REQUEST");
        assertThat(response.text("message")).isEqualTo("초대할 친구는 1명 이상 50명 이하여야 합니다.");
        assertThat(post("/match-rooms/leave", null, host.accessToken()).status()).isEqualTo(204);
    }
}
