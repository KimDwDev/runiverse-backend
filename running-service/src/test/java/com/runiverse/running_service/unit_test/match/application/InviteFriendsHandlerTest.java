package com.runiverse.running_service.unit_test.match.application;

import com.github.f4b6a3.uuid.UuidCreator;
import com.runiverse.running_service.application.match.command.invitesend.InviteFriendsCommand;
import com.runiverse.running_service.application.match.command.invitesend.InviteFriendsHandler;
import com.runiverse.running_service.application.match.command.invitesend.InviteFriendsResult;
import com.runiverse.running_service.application.match.command.invitesend.InviteFriendsResult.SentInvitation;
import com.runiverse.running_service.application.match.common.InviteProperties;
import com.runiverse.running_service.application.match.common.InviteStartPolicy;
import com.runiverse.running_service.application.match.exception.InviteRoomNotFoundException;
import com.runiverse.running_service.application.match.exception.NotFriendException;
import com.runiverse.running_service.application.match.exception.RunningInvitationClosedException;
import com.runiverse.running_service.application.match.port.out.LoadActiveUserIdsPort;
import com.runiverse.running_service.application.match.port.out.LoadFriendIdsPort;
import com.runiverse.running_service.application.match.port.out.LockMatchRoomPort;
import com.runiverse.running_service.application.match.port.out.UpdateMatchRoomPort;
import com.runiverse.running_service.application.match.common.MatchProperties;
import com.runiverse.running_service.application.match.common.MatchRoomChangedEvent;
import com.runiverse.running_service.application.match.common.RoomInfoAssembler;
import com.runiverse.running_service.application.match.port.out.RoomInfo;
import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.domain.running.player.vo.RunningPlayerId;
import com.runiverse.running_service.domain.running.room.InvitationDraft;
import com.runiverse.running_service.domain.running.room.RunningRoom;
import com.runiverse.running_service.domain.running.room.SessionDraft;
import com.runiverse.running_service.domain.running.room.vo.RunningRoomId;
import com.runiverse.running_service.domain.running.room.vo.RunningRoomInvitationStatus;
import com.runiverse.running_service.domain.running.room.vo.RunningRoomStatus;
import com.runiverse.running_service.domain.running.room.vo.RunningRoomType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
@DisplayName("친구 초대 단위 테스트")
class InviteFriendsHandlerTest {

    private static final long ROOM_ID = 130L;
    private static final int PACE = 330;
    private static final int TARGET_DISTANCE = 7_000;
    private static final int MAX_INVITE_COUNT = 5;
    private static final Duration CLOSE_OFFSET = Duration.ofMinutes(1);
    private static final MatchProperties MATCH_PROPERTIES = new MatchProperties(
            Duration.ofMinutes(10), Duration.ofSeconds(10), Duration.ofHours(6), 10, Duration.ofMinutes(20),
            LocalTime.of(18, 0), LocalTime.of(22, 0), Duration.ofMinutes(30), List.of(3_000, 5_000, 10_000));
    private static final InviteProperties INVITE_PROPERTIES = new InviteProperties(
            Duration.ofMinutes(10), CLOSE_OFFSET, 1_000, 42_195, MAX_INVITE_COUNT);

    @Mock
    private LockMatchRoomPort lockMatchRoomPort;

    @Mock
    private UpdateMatchRoomPort updateMatchRoomPort;

    @Mock
    private LoadFriendIdsPort loadFriendIdsPort;

    @Mock
    private LoadActiveUserIdsPort loadActiveUserIdsPort;

    @Mock
    private RoomInfoAssembler roomInfoAssembler;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private InviteFriendsHandler handler;
    private UserId inviter;
    private UserId friendA;
    private UserId friendB;

    @BeforeEach
    void setUp() {
        handler = new InviteFriendsHandler(
                lockMatchRoomPort, updateMatchRoomPort, loadFriendIdsPort, loadActiveUserIdsPort,
                new InviteStartPolicy(INVITE_PROPERTIES, MATCH_PROPERTIES), INVITE_PROPERTIES,
                roomInfoAssembler, eventPublisher);
        inviter = newUser();
        friendA = newUser();
        friendB = newUser();
    }

    private static UserId newUser() {
        return new UserId(UuidCreator.getTimeOrderedEpoch());
    }

    // 핸들러가 지금 시각을 직접 읽어 시작 시각을 지금 기준으로 잡는다 — 마감은 시작 1분 전이다
    private static LocalDateTime openStart() {
        return LocalDateTime.now().plusHours(1);
    }

    private RunningRoom room(RunningRoomType type, RunningRoomStatus status, LocalDateTime startAt,
                             List<SessionDraft> sessions, List<InvitationDraft> invitations) {
        return RunningRoom.builder()
                .runningRoomId(ROOM_ID)
                .type(type)
                .status(status)
                .startAt(startAt)
                .targetDistance(TARGET_DISTANCE)
                .avgPace(PACE)
                .currentPlayerCount((int) sessions.stream().filter(SessionDraft::connected).count())
                .maxPlayerCount(4)
                .sessions(sessions)
                .invitations(invitations)
                .build();
    }

    private RunningRoom recruitingRoom(List<InvitationDraft> invitations) {
        return room(RunningRoomType.INVITE, RunningRoomStatus.MATCHING, openStart(),
                List.of(new SessionDraft(inviter, new RunningPlayerId(1L), 0, true)), invitations);
    }

    private void givenLocked(RunningRoom room) {
        given(lockMatchRoomPort.lockById(new RunningRoomId(ROOM_ID))).willReturn(Optional.of(room));
    }

    private void givenFriends(UserId... friends) {
        given(loadFriendIdsPort.loadFriendIds(eq(inviter), anyCollection())).willReturn(Set.of(friends));
    }

    private void givenActive(UserId... active) {
        given(loadActiveUserIdsPort.loadActiveUserIds(anyCollection())).willReturn(Set.of(active));
    }

    private void givenAssembled() {
        given(roomInfoAssembler.assemble(any())).willReturn(new RoomInfo(
                ROOM_ID, RunningRoomType.INVITE, RunningRoomStatus.MATCHING, openStart(), openStart().minus(CLOSE_OFFSET),
                TARGET_DISTANCE, PACE, List.of(), List.of()));
    }

    private InviteFriendsResult invite(UserId... invitees) {
        return handler.handle(new InviteFriendsCommand(inviter.value(), ROOM_ID,
                Arrays.stream(invitees).map(UserId::value).toList()));
    }

    @Nested
    @DisplayName("성공 테스트")
    class SuccessTest {

        @Test
        @DisplayName("친구에게 보내면 대기 초대를 1회로 담아 돌려주고 방을 저장한 뒤 갱신을 알린다")
        void inviteFriends() {
            // given
            RunningRoom room = recruitingRoom(List.of());
            givenLocked(room);
            givenFriends(friendA, friendB);
            givenActive();
            givenAssembled();

            // when
            InviteFriendsResult result = invite(friendA, friendB);

            // then
            assertThat(result.invitations()).containsExactly(
                    new SentInvitation(friendA.value(), RunningRoomInvitationStatus.PENDING, 1),
                    new SentInvitation(friendB.value(), RunningRoomInvitationStatus.PENDING, 1));
            assertThat(room.getInvitations()).hasSize(2);
            verify(updateMatchRoomPort).update(room);
            verify(eventPublisher).publishEvent(any(MatchRoomChangedEvent.class));
        }

        @Test
        @DisplayName("같은 사람이 여러 번 들어 있으면 한 번만 보내고 요청 순서를 지킨다")
        void deduplicatesInvitees() {
            // given
            RunningRoom room = recruitingRoom(List.of());
            givenLocked(room);
            givenFriends(friendA, friendB);
            givenActive();
            givenAssembled();

            // when
            InviteFriendsResult result = invite(friendB, friendA, friendB);

            // then -> 두 번 세면 inviteCount가 2가 된다
            assertThat(result.invitations()).extracting(SentInvitation::userId)
                    .containsExactly(friendB.value(), friendA.value());
            assertThat(result.invitations()).extracting(SentInvitation::inviteCount).containsOnly(1);
            ArgumentCaptor<Collection<UserId>> asked = ArgumentCaptor.captor();
            verify(loadFriendIdsPort).loadFriendIds(eq(inviter), asked.capture());
            assertThat(asked.getValue()).containsExactly(friendB, friendA);
        }

        @Test
        @DisplayName("대기 중에 다시 보내면 상태는 대기 그대로이고 보낸 횟수가 이어서 오른다")
        void resendKeepsCounting() {
            // given
            givenLocked(recruitingRoom(List.of(
                    new InvitationDraft(inviter, friendA, RunningRoomInvitationStatus.PENDING, 2))));
            givenFriends(friendA);
            givenActive();
            givenAssembled();

            // when
            InviteFriendsResult result = invite(friendA);

            // then
            assertThat(result.invitations()).containsExactly(
                    new SentInvitation(friendA.value(), RunningRoomInvitationStatus.PENDING, 3));
        }

        @Test
        @DisplayName("활성 신청이 있는 친구는 건너뛰고 나머지에게만 보낸다")
        void skipsActiveFriends() {
            // given
            RunningRoom room = recruitingRoom(List.of());
            givenLocked(room);
            givenFriends(friendA, friendB);
            givenActive(friendA);
            givenAssembled();

            // when
            InviteFriendsResult result = invite(friendA, friendB);

            // then -> 오류가 아니라 응답에서 빠질 뿐이다
            assertThat(result.invitations()).extracting(SentInvitation::userId).containsExactly(friendB.value());
            assertThat(room.invitationStatusOf(friendA)).isEmpty();
        }

        @Test
        @DisplayName("보낸 횟수가 상한에 닿은 친구는 건너뛴다")
        void skipsReachedFriends() {
            // given
            givenLocked(recruitingRoom(List.of(new InvitationDraft(
                    inviter, friendA, RunningRoomInvitationStatus.DECLINED, MAX_INVITE_COUNT))));
            givenFriends(friendA, friendB);
            givenActive();
            givenAssembled();

            // when
            InviteFriendsResult result = invite(friendA, friendB);

            // then
            assertThat(result.invitations()).extracting(SentInvitation::userId).containsExactly(friendB.value());
        }

        @Test
        @DisplayName("모두 건너뛰면 빈 응답이고 저장도 알림도 하지 않는다")
        void allSkippedChangesNothing() {
            // given
            givenLocked(recruitingRoom(List.of()));
            givenFriends(friendA);
            givenActive(friendA);

            // when
            InviteFriendsResult result = invite(friendA);

            // then
            assertThat(result.invitations()).isEmpty();
            verify(updateMatchRoomPort, never()).update(any());
            verifyNoInteractions(roomInfoAssembler, eventPublisher);
        }
    }

    @Nested
    @DisplayName("실패 테스트")
    class FailureTest {

        @Test
        @DisplayName("방이 없으면 찾을 수 없다")
        void rejectsMissingRoom() {
            // given
            given(lockMatchRoomPort.lockById(new RunningRoomId(ROOM_ID))).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> invite(friendA))
                    .isInstanceOf(InviteRoomNotFoundException.class);
            verifyNoInteractions(loadFriendIdsPort, loadActiveUserIdsPort, updateMatchRoomPort);
        }

        @Test
        @DisplayName("매칭 방이면 찾을 수 없다")
        void rejectsMatchRoom() {
            // given
            givenLocked(room(RunningRoomType.MATCH, RunningRoomStatus.MATCHING, openStart(),
                    List.of(new SessionDraft(inviter, new RunningPlayerId(1L), 0, true)), List.of()));

            // when & then -> 초대 방이 아니면 있는지도 알려 주지 않는다
            assertThatThrownBy(() -> invite(friendA))
                    .isInstanceOf(InviteRoomNotFoundException.class);
            verifyNoInteractions(loadFriendIdsPort, updateMatchRoomPort);
        }

        @Test
        @DisplayName("호출자가 이 방 참가자가 아니면 찾을 수 없다")
        void rejectsStranger() {
            // given
            givenLocked(room(RunningRoomType.INVITE, RunningRoomStatus.MATCHING, openStart(),
                    List.of(new SessionDraft(newUser(), new RunningPlayerId(1L), 0, true)), List.of()));

            // when & then
            assertThatThrownBy(() -> invite(friendA))
                    .isInstanceOf(InviteRoomNotFoundException.class);
            verifyNoInteractions(loadFriendIdsPort, updateMatchRoomPort);
        }

        @Test
        @DisplayName("방을 나간 참가자는 찾을 수 없다")
        void rejectsLeftMember() {
            // given
            givenLocked(room(RunningRoomType.INVITE, RunningRoomStatus.MATCHING, openStart(), List.of(
                    new SessionDraft(newUser(), new RunningPlayerId(2L), 0, true),
                    new SessionDraft(inviter, new RunningPlayerId(1L), 1, false)), List.of()));

            // when & then
            assertThatThrownBy(() -> invite(friendA))
                    .isInstanceOf(InviteRoomNotFoundException.class);
        }

        @Test
        @DisplayName("모집이 끝난 방에는 초대할 수 없다")
        void rejectsClosedStatus() {
            // given
            givenLocked(room(RunningRoomType.INVITE, RunningRoomStatus.MATCHED, openStart(),
                    List.of(new SessionDraft(inviter, new RunningPlayerId(1L), 0, true)), List.of()));

            // when & then
            assertThatThrownBy(() -> invite(friendA))
                    .isInstanceOf(RunningInvitationClosedException.class);
            verifyNoInteractions(loadFriendIdsPort, updateMatchRoomPort);
        }

        @Test
        @DisplayName("모집 중이어도 마감 시각이 지났으면 초대할 수 없다")
        void rejectsPastCloseAt() {
            // given -> 시작 30초 전이면 마감(1분 전)이 이미 지났다. 마감 예약이 아직 안 돈 상황이다
            givenLocked(room(RunningRoomType.INVITE, RunningRoomStatus.MATCHING,
                    LocalDateTime.now().plusSeconds(30),
                    List.of(new SessionDraft(inviter, new RunningPlayerId(1L), 0, true)), List.of()));

            // when & then
            assertThatThrownBy(() -> invite(friendA))
                    .isInstanceOf(RunningInvitationClosedException.class);
            verifyNoInteractions(loadFriendIdsPort, updateMatchRoomPort);
        }

        @Test
        @DisplayName("친구가 아닌 사람이 하나라도 섞이면 아무에게도 보내지 않는다")
        void rejectsNonFriend() {
            // given
            RunningRoom room = recruitingRoom(List.of());
            givenLocked(room);
            givenFriends(friendA);

            // when & then
            assertThatThrownBy(() -> invite(friendA, friendB))
                    .isInstanceOf(NotFriendException.class);
            assertThat(room.getInvitations()).isEmpty();
            verifyNoInteractions(loadActiveUserIdsPort, updateMatchRoomPort, eventPublisher);
        }

        @Test
        @DisplayName("자기 자신을 넣으면 친구가 아니라 거절한다")
        void rejectsSelf() {
            // given
            givenLocked(recruitingRoom(List.of()));
            givenFriends();

            // when & then -> 도메인의 자기 초대 예외(500)까지 가지 않고 409로 끝난다
            assertThatThrownBy(() -> invite(inviter))
                    .isInstanceOf(NotFriendException.class);
            verifyNoInteractions(updateMatchRoomPort);
        }
    }
}
