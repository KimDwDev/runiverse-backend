package com.runiverse.running_service.unit_test.match.application;

import com.github.f4b6a3.uuid.UuidCreator;
import com.runiverse.running_service.application.device.command.push.RoomChatPushRequestedEvent;
import com.runiverse.running_service.application.device.port.out.PushMessage;
import com.runiverse.running_service.application.device.port.out.PushType;
import com.runiverse.running_service.application.match.common.RoomChatPushRequester;
import com.runiverse.running_service.application.match.port.out.RoomChat;
import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.domain.running.room.vo.RunningRoomChatType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("방 채팅 푸시 요청 단위 테스트")
class RoomChatPushRequesterTest {

    private static final long ROOM_ID = 125L;
    private static final LocalDateTime START = LocalDateTime.of(2026, 11, 20, 19, 30);
    private static final UUID CHULSU = UuidCreator.getTimeOrderedEpoch();
    private static final UUID YOUNGHEE = UuidCreator.getTimeOrderedEpoch();
    private static final UUID MINSU = UuidCreator.getTimeOrderedEpoch();
    private static final List<UserId> MEMBERS = List.of(new UserId(CHULSU), new UserId(YOUNGHEE), new UserId(MINSU));

    private final List<RoomChatPushRequestedEvent> published = new ArrayList<>();
    private RoomChatPushRequester requester;

    @BeforeEach
    void setUp() {
        requester = new RoomChatPushRequester(event -> published.add((RoomChatPushRequestedEvent) event));
    }

    private static RoomChat message(String content) {
        return new RoomChat(3021L, ROOM_ID, RunningRoomChatType.MESSAGE,
                new RoomChat.Person(CHULSU, "철수", "https://cdn/profiles/c.png", false), null,
                content, false, null, START.minusMinutes(20));
    }

    private static RoomChat notice(RunningRoomChatType type) {
        return new RoomChat(3022L, ROOM_ID, type,
                null, new RoomChat.Person(CHULSU, "철수", null, false),
                null, false, null, START.minusMinutes(20));
    }

    @Test
    @DisplayName("말은 방의 다른 사람마다 한 건씩 내용 그대로 보내고 시작 시각에 만료된다")
    void messageGoesToOthers() {
        // when
        requester.request(message("곧 출발해요"), START, 5_000, MEMBERS);

        // then -> 보낸 사람은 받지 않는다
        assertThat(published).extracting(RoomChatPushRequestedEvent::userId).containsExactly(YOUNGHEE, MINSU);
        PushMessage push = published.getFirst().message();
        assertThat(push.type()).isEqualTo(PushType.ROOM_CHAT_SENT);
        assertThat(push.title()).isEqualTo("19:30 - 5 km 러닝");
        assertThat(push.body()).isEqualTo("곧 출발해요");
        assertThat(push.expiresAt()).isEqualTo(START);
        assertThat(push.data())
                .containsEntry("runningRoomId", "125")
                .containsEntry("chatId", "3021")
                .containsEntry("chatType", "MESSAGE")
                .containsEntry("actorId", CHULSU.toString())
                .containsEntry("actorNickname", "철수")
                .containsEntry("actorProfileImageUrl", "https://cdn/profiles/c.png");
    }

    @Test
    @DisplayName("처음 들어온 줄은 들어온 사람 이름으로 본문을 만들고 사진이 없으면 사진 키를 싣지 않는다")
    void joinedNotice() {
        // when
        requester.request(notice(RunningRoomChatType.JOINED), START, 5_000, MEMBERS);

        // then
        PushMessage push = published.getFirst().message();
        assertThat(push.body()).isEqualTo("철수님이 들어왔어요");
        assertThat(push.data())
                .containsEntry("chatType", "JOINED")
                .containsEntry("actorId", CHULSU.toString())
                .doesNotContainKey("actorProfileImageUrl");
    }

    @Test
    @DisplayName("제목 거리는 앱 표기와 같다 — 딱 떨어지지 않으면 소수, 모르면 거리 없이")
    void titleDistance() {
        // when
        requester.request(message("가"), START, 21_098, MEMBERS);
        requester.request(message("나"), START, 10_000, MEMBERS);
        requester.request(message("다"), START, null, MEMBERS);

        // then
        assertThat(published).extracting(event -> event.message().title()).containsExactly(
                "19:30 - 21.098 km 러닝", "19:30 - 21.098 km 러닝",
                "19:30 - 10 km 러닝", "19:30 - 10 km 러닝",
                "19:30 러닝", "19:30 러닝");
    }

    @Test
    @DisplayName("방에 당사자뿐이면 보내지 않는다")
    void nobodyElse() {
        // when
        requester.request(message("혼자예요"), START, 5_000, List.of(new UserId(CHULSU)));

        // then
        assertThat(published).isEmpty();
    }

    @Test
    @DisplayName("다시 들어온 줄·나간 줄은 푸시하지 않는 줄이라 거부한다")
    void rejectsSilentNotice() {
        // when & then
        assertThatThrownBy(() -> requester.request(notice(RunningRoomChatType.REJOINED), START, 5_000, MEMBERS))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> requester.request(notice(RunningRoomChatType.LEFT), START, 5_000, MEMBERS))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(published).isEmpty();
    }
}
