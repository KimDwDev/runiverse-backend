package com.runiverse.e2e.match;

import com.runiverse.e2e.E2eTestSupport;
import com.runiverse.e2e.SseStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

// 대기방 채팅 보내기(5-E-2)와 ROOM_CHAT_SENT — 실제 DB(IDENTITY·created_at)·Redis 왕복·보안 필터를 함께 탄다
@DisplayName("배포 이미지 대상 대기방 채팅 E2E 테스트")
class RoomChatE2eTest extends E2eTestSupport {

    private static final ZoneId APP_ZONE = ZoneId.of("Asia/Seoul");
    // 다른 매칭 E2E와 한 방에 합쳐지지 않게 아무도 쓰지 않는 내일 슬롯·거리를 쓴다
    private static final LocalDateTime CHAT_SLOT = LocalDate.now(APP_ZONE).plusDays(1).atTime(19, 30);
    private static final int DISTANCE = 5000;

    @Test
    @DisplayName("보낸 채팅은 201로 돌아오고 같은 방 다른 참가자에게만 ROOM_CHAT_SENT로 간다")
    void sentChatReachesOthersButNotSender() {
        // given - 두 사람이 같은 매칭 방에 있고 둘 다 스트림을 열었다
        TestUser host = signUpAndOnboard();
        TestUser guest = signUpAndOnboard();
        int runningRoomId = apply(host).number("runningRoomId");
        assertThat(apply(guest).number("runningRoomId")).isEqualTo(runningRoomId);

        try (SseStream hostStream = openMatchStream(host.accessToken());
             SseStream guestStream = openMatchStream(guest.accessToken())) {
            // 입장 이벤트는 채팅과 무관하다 — 연결이 자리 잡았는지만 확인한다
            assertThat(hostStream.await("MATCH_ROOM_ENTRANCE").get("runningRoomId")).isEqualTo(runningRoomId);
            assertThat(guestStream.await("MATCH_ROOM_ENTRANCE").get("runningRoomId")).isEqualTo(runningRoomId);

            // when - guest가 보낸다
            Response sent = post(chatsPath(runningRoomId), Map.of("content", "곧 출발해요"), guest.accessToken());

            // then - 응답이 명세의 Chat이다. ID·보낸 시각은 실제 DB가 만든다
            assertThat(sent.status()).isEqualTo(201);
            int chatId = sent.number("chatId");
            assertThat(sent.number("runningRoomId")).isEqualTo(runningRoomId);
            assertThat(sent.text("content")).isEqualTo("곧 출발해요");
            assertThat(sent.bool("isDeleted")).isFalse();
            assertThat(sent.body()).containsEntry("editedAt", null);
            assertThat(LocalDateTime.parse(sent.text("createdAt"))).isNotNull();
            assertThat(sender(sent.body()))
                    .containsEntry("userId", guest.userId())
                    .containsEntry("nickname", guest.nickname())
                    .containsEntry("isDeleted", false);

            // then - host는 Redis를 돌아온 같은 채팅을 받는다
            Map<String, Object> received = hostStream.await("ROOM_CHAT_SENT");
            assertThat(received).isEqualTo(sent.body());

            // then - guest는 자기 채팅을 받지 않는다: 뒤이어 host가 보낸 것이 guest의 첫 채팅 이벤트다
            Response reply = post(chatsPath(runningRoomId), Map.of("content", "네 좋아요"), host.accessToken());
            assertThat(reply.status()).isEqualTo(201);
            Map<String, Object> guestFirst = guestStream.await("ROOM_CHAT_SENT");
            assertThat(((Number) guestFirst.get("chatId")).intValue()).isEqualTo(reply.number("chatId"));
            assertThat(reply.number("chatId")).isGreaterThan(chatId);   // 생성 순서대로 커진다

            // then - 저장된 행이 응답과 같다
            List<String> rows = querySql("""
                    SELECT running_room_id, user_id, content, edited_at IS NULL, deleted_at IS NULL
                    FROM running_room_chats WHERE running_room_chat_id = %d
                    """.formatted(chatId));
            assertThat(rows).containsExactly("%d|%s|곧 출발해요|t|t".formatted(runningRoomId, guest.userId()));
        } finally {
            leave(host);
            leave(guest);
        }
    }

    @Test
    @DisplayName("방 밖이면 404, 빈 내용은 400, 토큰이 없으면 401이다")
    void rejectsOutsidersAndBadRequests() {
        // given
        TestUser member = signUpAndOnboard();
        TestUser outsider = signUpAndOnboard();
        int runningRoomId = apply(member).number("runningRoomId");

        try {
            // when
            Response notInRoom = post(chatsPath(runningRoomId), Map.of("content", "안녕하세요"), outsider.accessToken());
            Response blank = post(chatsPath(runningRoomId), Map.of("content", "   "), member.accessToken());
            Response tooLong = post(chatsPath(runningRoomId), Map.of("content", "가".repeat(201)), member.accessToken());
            Response noToken = post(chatsPath(runningRoomId), Map.of("content", "안녕하세요"), null);

            // then - 남의 방이 있는지 없는지를 알려 주지 않는다
            assertThat(notInRoom.status()).isEqualTo(404);
            assertThat(notInRoom.text("code")).isEqualTo("NOT_FOUND");
            assertThat(blank.status()).isEqualTo(400);
            assertThat(blank.text("message")).isEqualTo("메시지 내용은 필수입니다.");
            assertThat(tooLong.status()).isEqualTo(400);
            assertThat(tooLong.text("message")).isEqualTo("메시지는 200자 이하여야 합니다.");
            assertThat(noToken.status()).isEqualTo(401);
            assertThat(querySql("SELECT count(*) FROM running_room_chats WHERE running_room_id = " + runningRoomId))
                    .containsExactly("0");
        } finally {
            leave(member);
        }
    }

    @Test
    @DisplayName("러닝이 시작된 방에서는 배정이 남아 있어도 409 ROOM_CHAT_CLOSED다")
    void closedAfterRunningStarts() {
        // given - 시작 스케줄러를 기다리지 않고 방 상태만 시작으로 바꾼다(배정·신청은 그대로다)
        TestUser member = signUpAndOnboard();
        int runningRoomId = apply(member).number("runningRoomId");
        executeSql("UPDATE running_rooms SET status = 'STARTED' WHERE running_room_id = " + runningRoomId);

        try {
            // when
            Response response = post(chatsPath(runningRoomId), Map.of("content", "출발!"), member.accessToken());

            // then - 가려져 500이 되면 클라가 채팅 입력을 닫을지 가를 수 없다
            assertThat(response.status()).isEqualTo(409);
            assertThat(response.text("code")).isEqualTo("ROOM_CHAT_CLOSED");
            assertThat(response.text("message")).isEqualTo("지금은 채팅할 수 없습니다.");
        } finally {
            // 다른 테스트가 같은 슬롯에 붙지 않도록 모집 중으로 되돌린 뒤 나간다
            executeSql("UPDATE running_rooms SET status = 'MATCHING' WHERE running_room_id = " + runningRoomId);
            leave(member);
        }
    }

    private Response apply(TestUser user) {
        Response response = post("/match-rooms", Map.of(
                "scheduledStartAt", CHAT_SLOT.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                "targetDistanceMeters", DISTANCE
        ), user.accessToken());
        assertThat(response.status()).isEqualTo(201);
        return response;
    }

    private void leave(TestUser user) {
        post("/match-rooms/leave", null, user.accessToken());
    }

    private static String chatsPath(int runningRoomId) {
        return "/match-rooms/" + runningRoomId + "/chats";
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> sender(Map<String, Object> chat) {
        return (Map<String, Object>) chat.get("sender");
    }
}
