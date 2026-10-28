package com.runiverse.running_service.unit_test.notification.application;

import ch.qos.logback.classic.Level;
import com.github.f4b6a3.uuid.UuidCreator;
import com.runiverse.running_service.application.common.port.out.LoadPlayerProfilesPort;
import com.runiverse.running_service.application.common.port.out.PlayerProfile;
import com.runiverse.running_service.application.notification.common.NotificationProperties;
import com.runiverse.running_service.application.notification.exception.InvalidNotificationCursorException;
import com.runiverse.running_service.application.notification.port.out.CountUnreadUserNotificationsPort;
import com.runiverse.running_service.application.notification.port.out.InvitationSnapshot;
import com.runiverse.running_service.application.notification.port.out.LoadInvitationSnapshotsPort;
import com.runiverse.running_service.application.notification.port.out.LoadRelationshipTypesPort;
import com.runiverse.running_service.application.notification.port.out.LoadUserNotificationsPort;
import com.runiverse.running_service.application.notification.port.out.UserNotificationPage;
import com.runiverse.running_service.application.notification.port.out.UserNotificationRow;
import com.runiverse.running_service.application.notification.query.list.GetUserNotificationsHandler;
import com.runiverse.running_service.application.notification.query.list.GetUserNotificationsQuery;
import com.runiverse.running_service.application.notification.query.list.GetUserNotificationsResult;
import com.runiverse.running_service.application.notification.query.list.NotificationCursor;
import com.runiverse.running_service.application.user.port.out.GenerateViewUrlPort;
import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.domain.notification.vo.FriendRequestedPayload;
import com.runiverse.running_service.domain.notification.vo.RunningInvitedPayload;
import com.runiverse.running_service.domain.notification.vo.UserNotificationType;
import com.runiverse.running_service.domain.relationship.vo.RelationshipType;
import com.runiverse.running_service.domain.running.room.vo.RoomInvitationStatus;
import com.runiverse.running_service.domain.running.room.vo.RunningRoomId;
import com.runiverse.running_service.support.LogCapture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
@DisplayName("알림 목록 조회 단위 테스트")
class GetUserNotificationsHandlerTest {

    private static final UUID ME = UuidCreator.getTimeOrderedEpoch();
    private static final UUID CHULSU = UuidCreator.getTimeOrderedEpoch();
    private static final long ROOM_ID = 130L;
    private static final int LIMIT = 20;
    private static final LocalDateTime START = LocalDateTime.of(2026, 10, 28, 19, 30);
    private static final Duration RETENTION = Duration.ofDays(30);

    @Mock
    private LoadUserNotificationsPort loadUserNotificationsPort;

    @Mock
    private CountUnreadUserNotificationsPort countUnreadUserNotificationsPort;

    @Mock
    private LoadPlayerProfilesPort loadPlayerProfilesPort;

    @Mock
    private LoadRelationshipTypesPort loadRelationshipTypesPort;

    @Mock
    private LoadInvitationSnapshotsPort loadInvitationSnapshotsPort;

    @Mock
    private GenerateViewUrlPort generateViewUrlPort;

    private GetUserNotificationsHandler handler;
    private LogCapture log;

    @BeforeEach
    void setUp() {
        handler = new GetUserNotificationsHandler(loadUserNotificationsPort, countUnreadUserNotificationsPort,
                loadPlayerProfilesPort, loadRelationshipTypesPort, loadInvitationSnapshotsPort,
                generateViewUrlPort, new NotificationProperties(RETENTION));
        log = LogCapture.of(GetUserNotificationsHandler.class);
    }

    @AfterEach
    void tearDown() {
        log.stop();
    }

    private static UserNotificationRow friendRequested(long id) {
        return new UserNotificationRow(id, new FriendRequestedPayload(new UserId(CHULSU)), false,
                LocalDateTime.of(2026, 10, 28, 12, 0));
    }

    private static UserNotificationRow invited(long id) {
        return new UserNotificationRow(id,
                new RunningInvitedPayload(new UserId(CHULSU), new RunningRoomId(ROOM_ID)), true,
                LocalDateTime.of(2026, 10, 28, 13, 0));
    }

    private void givenPage(List<UserNotificationRow> rows, Long nextBeforeId) {
        given(loadUserNotificationsPort.loadPage(eq(new UserId(ME)), any(), any(), eq(LIMIT)))
                .willReturn(new UserNotificationPage(rows, nextBeforeId));
    }

    private void givenChulsuAlive(String profileImageKey) {
        given(loadPlayerProfilesPort.loadProfiles(Set.of(CHULSU)))
                .willReturn(Map.of(CHULSU, new PlayerProfile(CHULSU, "철수", profileImageKey, null)));
    }

    private GetUserNotificationsResult firstPage() {
        return handler.handle(new GetUserNotificationsQuery(ME, null, LIMIT));
    }

    @Test
    @DisplayName("친구 요청 알림에 일으킨 사람의 프로필과 지금 관계를 붙이고 안 읽은 수를 함께 준다")
    void assemblesFriendRequestedItem() {
        // given
        givenPage(List.of(friendRequested(912L)), null);
        givenChulsuAlive("profile/chulsu.jpg");
        given(generateViewUrlPort.generate("profile/chulsu.jpg")).willReturn("https://cdn/chulsu.jpg");
        given(loadRelationshipTypesPort.loadTypes(new UserId(ME), Set.of(CHULSU)))
                .willReturn(Map.of(CHULSU, RelationshipType.FRIEND_REQUEST_RECEIVED));
        given(countUnreadUserNotificationsPort.countUnread(eq(new UserId(ME)), any())).willReturn(3L);

        // when
        GetUserNotificationsResult result = firstPage();

        // then
        assertThat(result.items()).singleElement().satisfies(item -> {
            assertThat(item.notificationId()).isEqualTo(912L);
            assertThat(item.type()).isEqualTo(UserNotificationType.FRIEND_REQUESTED);
            assertThat(item.read()).isFalse();
            assertThat(item.actor()).isEqualTo(new GetUserNotificationsResult.Actor(
                    CHULSU, "철수", "https://cdn/chulsu.jpg", false, RelationshipType.FRIEND_REQUEST_RECEIVED));
            assertThat(item.invitation()).isNull();
        });
        assertThat(result.nextCursor()).isNull();
        assertThat(result.unreadCount()).isEqualTo(3L);
        // 친구 알림뿐이라 초대는 읽지 않는다
        verifyNoInteractions(loadInvitationSnapshotsPort);
    }

    @Test
    @DisplayName("초대 알림에는 받은 초대의 지금 상태와 방 정보를 붙인다")
    void assemblesInvitationItem() {
        // given
        givenPage(List.of(invited(913L)), null);
        givenChulsuAlive(null);
        given(loadRelationshipTypesPort.loadTypes(new UserId(ME), Set.of(CHULSU)))
                .willReturn(Map.of(CHULSU, RelationshipType.FRIEND));
        given(loadInvitationSnapshotsPort.loadSnapshots(new UserId(ME), Set.of(ROOM_ID))).willReturn(Map.of(
                ROOM_ID, new InvitationSnapshot(ROOM_ID, RoomInvitationStatus.EXPIRED, START, 7_000)));

        // when
        GetUserNotificationsResult result = firstPage();

        // then -> 알림은 사건의 기록이고 버튼은 지금 상태(EXPIRED)로 그린다
        assertThat(result.items()).singleElement().satisfies(item -> {
            assertThat(item.type()).isEqualTo(UserNotificationType.RUNNING_INVITED);
            assertThat(item.invitation()).isEqualTo(new GetUserNotificationsResult.Invitation(
                    ROOM_ID, RoomInvitationStatus.EXPIRED, START, 7_000));
            assertThat(item.actor().relationshipType()).isEqualTo(RelationshipType.FRIEND);
            assertThat(item.actor().profileImageUrl()).isNull();
        });
        verifyNoInteractions(generateViewUrlPort);
    }

    @Test
    @DisplayName("일으킨 사람이 탈퇴했으면 탈퇴 유저 형식으로 싣고 관계는 싣지 않는다")
    void marksDeletedActor() {
        // given -> 탈퇴하면 users 행이 지워져 프로필이 나오지 않는다
        givenPage(List.of(friendRequested(912L)), null);
        given(loadPlayerProfilesPort.loadProfiles(Set.of(CHULSU))).willReturn(Map.of());
        given(loadRelationshipTypesPort.loadTypes(new UserId(ME), Set.of(CHULSU))).willReturn(Map.of());

        // when
        GetUserNotificationsResult result = firstPage();

        // then -> 알림은 남고 사람만 §0 탈퇴 유저 형식이다
        assertThat(result.items()).singleElement().extracting(GetUserNotificationsResult.Item::actor)
                .isEqualTo(new GetUserNotificationsResult.Actor(CHULSU, "탈퇴한 사용자", null, true, null));
    }

    @Test
    @DisplayName("관계 행이 없는 사람은 NONE으로 본다")
    void missingRelationshipIsNone() {
        // given
        givenPage(List.of(friendRequested(912L)), null);
        givenChulsuAlive(null);
        given(loadRelationshipTypesPort.loadTypes(new UserId(ME), Set.of(CHULSU))).willReturn(Map.of());

        // when & then
        assertThat(firstPage().items()).singleElement()
                .extracting(item -> item.actor().relationshipType()).isEqualTo(RelationshipType.NONE);
    }

    @Test
    @DisplayName("초대 행이 사라진 초대 알림은 그 항목만 빼고 경고를 남긴다")
    void skipsInvitationWithoutRow() {
        // given
        givenPage(List.of(friendRequested(912L), invited(913L)), null);
        givenChulsuAlive(null);
        given(loadRelationshipTypesPort.loadTypes(new UserId(ME), Set.of(CHULSU))).willReturn(Map.of());
        given(loadInvitationSnapshotsPort.loadSnapshots(new UserId(ME), Set.of(ROOM_ID))).willReturn(Map.of());

        // when
        GetUserNotificationsResult result = firstPage();

        // then -> 알림 하나 때문에 알림함 전체가 막히지 않는다
        assertThat(result.items()).extracting(GetUserNotificationsResult.Item::notificationId).containsExactly(912L);
        assertThat(log.messages(Level.WARN)).containsExactly(
                "[알림] 목록 항목 건너뜀: 초대 행 없음 - notificationId=913, runningRoomId=" + ROOM_ID);
    }

    @Test
    @DisplayName("다음 페이지가 있으면 어댑터가 정한 기준으로 커서를 만든다")
    void encodesNextCursor() {
        // given
        givenPage(List.of(friendRequested(912L)), 912L);
        givenChulsuAlive(null);
        given(loadRelationshipTypesPort.loadTypes(new UserId(ME), Set.of(CHULSU))).willReturn(Map.of());

        // when
        GetUserNotificationsResult result = firstPage();

        // then
        assertThat(NotificationCursor.decode(result.nextCursor()).beforeId()).isEqualTo(912L);
    }

    @Test
    @DisplayName("커서를 풀어 그 ID보다 오래된 알림을 보관 기간 안에서 읽고, 안 읽은 수도 같은 경계로 센다")
    void decodesCursorAndUsesSameSince() {
        // given
        given(loadUserNotificationsPort.loadPage(eq(new UserId(ME)), eq(500L), any(), eq(LIMIT)))
                .willReturn(new UserNotificationPage(List.of(), null));
        LocalDateTime before = LocalDateTime.now();

        // when
        handler.handle(new GetUserNotificationsQuery(ME, new NotificationCursor(500L).encode(), LIMIT));

        // then
        ArgumentCaptor<LocalDateTime> pageSince = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> countSince = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(loadUserNotificationsPort).loadPage(eq(new UserId(ME)), eq(500L), pageSince.capture(), eq(LIMIT));
        verify(countUnreadUserNotificationsPort).countUnread(eq(new UserId(ME)), countSince.capture());
        assertThat(pageSince.getValue()).isEqualTo(countSince.getValue());
        assertThat(pageSince.getValue()).isBetween(before.minus(RETENTION), LocalDateTime.now().minus(RETENTION));
    }

    @Test
    @DisplayName("알림이 없으면 사람·관계·초대를 읽지 않는다")
    void emptyPageSkipsLookups() {
        // given
        given(loadUserNotificationsPort.loadPage(eq(new UserId(ME)), isNull(), any(), eq(LIMIT)))
                .willReturn(new UserNotificationPage(List.of(), null));

        // when
        GetUserNotificationsResult result = firstPage();

        // then
        assertThat(result.items()).isEmpty();
        assertThat(result.nextCursor()).isNull();
        verifyNoInteractions(loadPlayerProfilesPort, loadRelationshipTypesPort, loadInvitationSnapshotsPort);
    }

    @Test
    @DisplayName("깨진 커서는 400으로 거절하고 아무것도 읽지 않는다")
    void rejectsBrokenCursor() {
        // when & then
        assertThatThrownBy(() -> handler.handle(new GetUserNotificationsQuery(ME, "!!!", LIMIT)))
                .isInstanceOf(InvalidNotificationCursorException.class);
        verifyNoInteractions(loadUserNotificationsPort, countUnreadUserNotificationsPort);
    }
}
