package com.runiverse.running_service.unit_test.app.presentation;

import com.github.f4b6a3.uuid.UuidCreator;
import com.runiverse.running_service.application.app.port.out.AppStreamEvent;
import com.runiverse.running_service.application.notification.query.list.GetUserNotificationsResult;
import com.runiverse.running_service.application.running.command.activity.RunningActivity;
import com.runiverse.running_service.domain.notification.vo.UserNotificationType;
import com.runiverse.running_service.domain.relationship.vo.RelationshipType;
import com.runiverse.running_service.domain.running.room.vo.RoomInvitationStatus;
import com.runiverse.running_service.presentation.app.sse.SseAppStreamConnection;
import com.runiverse.running_service.presentation.notification.response.NotificationEventResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("앱 스트림 SSE 전송 단위 테스트")
class SseAppStreamConnectionTest {

    @Mock
    private SseEmitter emitter;

    private SseAppStreamConnection connection;

    @BeforeEach
    void setUp() {
        connection = new SseAppStreamConnection("conn-1", emitter);
    }

    @Test
    @DisplayName("keep-alive는 이벤트가 아니라 주석 라인을 보낸다")
    void keepAliveSendsComment() throws Exception {
        // when
        connection.keepAlive();

        // then -> 클라 이벤트 핸들러가 깨지 않는다
        ArgumentCaptor<SseEmitter.SseEventBuilder> sent = ArgumentCaptor.forClass(SseEmitter.SseEventBuilder.class);
        verify(emitter).send(sent.capture());
        String frame = sent.getValue().build().stream()
                .map(ResponseBodyEmitter.DataWithMediaType::getData)
                .map(String::valueOf)
                .reduce("", String::concat);
        assertThat(frame).startsWith(":ping");
    }

    @Test
    @DisplayName("전송이 실패하면 스스로 닫아 레지스트리에서 빠지게 한다")
    void completesWhenSendFails() throws Exception {
        // given -> 끊긴 단말이다
        willThrow(new IOException("broken pipe")).given(emitter).send(any(SseEmitter.SseEventBuilder.class));

        // when
        connection.keepAlive();

        // then -> complete가 onCompletion을 돌려 정리가 한 곳으로 모인다
        verify(emitter).complete();
    }

    @Test
    @DisplayName("밀려난 연결은 닫힌다")
    void closesWhenSuperseded() {
        // when
        connection.closeSuperseded();

        // then
        verify(emitter).complete();
    }

    @Test
    @DisplayName("새 알림은 알림 종류를 이벤트 이름으로, 알림 목록 항목에서 type만 뺀 JSON을 싣는다")
    void notificationCreatedUsesListItemShape() throws Exception {
        // given
        UUID chulsu = UuidCreator.getTimeOrderedEpoch();
        GetUserNotificationsResult.Item item = new GetUserNotificationsResult.Item(
                913L, UserNotificationType.RUNNING_INVITED, false, LocalDateTime.of(2026, 10, 28, 12, 3),
                new GetUserNotificationsResult.Actor(chulsu, "철수", null, false, RelationshipType.FRIEND),
                new GetUserNotificationsResult.Invitation(130L, RoomInvitationStatus.PENDING,
                        LocalDateTime.of(2026, 10, 28, 19, 30), 7_000));

        // when
        connection.send(AppStreamEvent.notification(item));

        // then -> 클라가 이벤트 이름을 type으로 붙여 목록 맨 위에 넣는다
        ArgumentCaptor<SseEmitter.SseEventBuilder> sent = ArgumentCaptor.forClass(SseEmitter.SseEventBuilder.class);
        verify(emitter).send(sent.capture());
        Set<ResponseBodyEmitter.DataWithMediaType> parts = sent.getValue().build();
        String frame = parts.stream()
                .filter(part -> !MediaType.APPLICATION_JSON.equals(part.getMediaType()))
                .map(part -> String.valueOf(part.getData()))
                .reduce("", String::concat);
        Object body = parts.stream()
                .filter(part -> MediaType.APPLICATION_JSON.equals(part.getMediaType()))
                .map(ResponseBodyEmitter.DataWithMediaType::getData)
                .findFirst()
                .orElseThrow();
        assertThat(frame).contains("event:RUNNING_INVITED");
        assertThat(body).isEqualTo(NotificationEventResponse.from(item));
        JsonNode json = JsonMapper.builder().build().valueToTree(body);
        assertThat(json.has("type")).isFalse();   // 종류는 event 이름이 말한다
        assertThat(json.get("isRead").asBoolean()).isFalse();
        assertThat(json.get("actor").get("relationshipType").asString()).isEqualTo("FRIEND");
        assertThat(json.get("invitation").get("status").asString()).isEqualTo("PENDING");
    }

    @Test
    @DisplayName("홈 대기 인원은 HOME_WAITING 이벤트에 waitingCount 하나만 싣는다")
    void homeWaitingCarriesCountOnly() throws Exception {
        // when
        connection.send(AppStreamEvent.homeWaiting(4));

        // then
        JsonNode json = sentJson("event:HOME_WAITING");
        assertThat(json.propertyNames()).containsExactly("waitingCount");
        assertThat(json.get("waitingCount").asInt()).isEqualTo(4);
    }

    @Test
    @DisplayName("홈 러닝 현황은 HOME_ACTIVITY 이벤트에 숫자와 가린 닉네임의 완주 소식을 싣는다")
    void homeActivityCarriesCountsAndFinishedRuns() throws Exception {
        // given
        RunningActivity activity = new RunningActivity(
                3, List.of(new RunningActivity.FinishedRun(101L, "철**", 5_000)), 7, 5);

        // when
        connection.send(AppStreamEvent.homeActivity(activity));

        // then
        JsonNode json = sentJson("event:HOME_ACTIVITY");
        assertThat(json.propertyNames()).containsExactly(
                "runningNowCount", "todayFinishedRuns", "todayFinishedCount", "yesterdayFinishedCount");
        assertThat(json.get("runningNowCount").asInt()).isEqualTo(3);
        assertThat(json.get("todayFinishedCount").asInt()).isEqualTo(7);
        assertThat(json.get("yesterdayFinishedCount").asInt()).isEqualTo(5);
        JsonNode run = json.get("todayFinishedRuns").get(0);
        assertThat(run.propertyNames()).containsExactly("runningRecordId", "nickname", "distanceMeters");
        assertThat(run.get("runningRecordId").asLong()).isEqualTo(101L);
        assertThat(run.get("nickname").asString()).isEqualTo("철**");
        assertThat(run.get("distanceMeters").asInt()).isEqualTo(5_000);
    }

    // 보낸 프레임의 이벤트 이름을 확인하고 JSON data를 꺼낸다
    private JsonNode sentJson(String eventLine) throws IOException {
        ArgumentCaptor<SseEmitter.SseEventBuilder> sent = ArgumentCaptor.forClass(SseEmitter.SseEventBuilder.class);
        verify(emitter).send(sent.capture());
        Set<ResponseBodyEmitter.DataWithMediaType> parts = sent.getValue().build();
        String frame = parts.stream()
                .filter(part -> !MediaType.APPLICATION_JSON.equals(part.getMediaType()))
                .map(part -> String.valueOf(part.getData()))
                .reduce("", String::concat);
        assertThat(frame).contains(eventLine);
        Object body = parts.stream()
                .filter(part -> MediaType.APPLICATION_JSON.equals(part.getMediaType()))
                .map(ResponseBodyEmitter.DataWithMediaType::getData)
                .findFirst()
                .orElseThrow();
        return JsonMapper.builder().build().valueToTree(body);
    }
}
