package com.runiverse.running_service.integration_test.match;

import com.runiverse.running_service.application.auth.command.signup.SignUpCommand;
import com.runiverse.running_service.application.auth.command.signup.SignUpHandler;
import com.runiverse.running_service.application.match.command.inviteaccept.AcceptInvitationCommand;
import com.runiverse.running_service.application.match.command.inviteaccept.AcceptInvitationHandler;
import com.runiverse.running_service.application.match.command.invitedecline.DeclineInvitationCommand;
import com.runiverse.running_service.application.match.command.invitedecline.DeclineInvitationHandler;
import com.runiverse.running_service.application.match.command.inviteopen.OpenInviteRoomCommand;
import com.runiverse.running_service.application.match.command.inviteopen.OpenInviteRoomHandler;
import com.runiverse.running_service.application.match.command.invitesend.InviteFriendsCommand;
import com.runiverse.running_service.application.match.command.invitesend.InviteFriendsHandler;
import com.runiverse.running_service.application.match.command.invitesend.InviteFriendsResult;
import com.runiverse.running_service.application.match.command.invitesend.InviteFriendsResult.SentInvitation;
import com.runiverse.running_service.application.match.common.InviteProperties;
import com.runiverse.running_service.application.match.common.InviteStartPolicy;
import com.runiverse.running_service.application.match.exception.InvitationNotFoundException;
import com.runiverse.running_service.application.match.exception.InvitationNotPendingException;
import com.runiverse.running_service.application.match.exception.NotFriendException;
import com.runiverse.running_service.application.match.exception.RunningRoomFullException;
import com.runiverse.running_service.application.match.command.broadcast.BroadMatchEventHandler;
import com.runiverse.running_service.application.match.command.broadcast.BroadcastMatchEventCommand;
import com.runiverse.running_service.application.match.command.cancel.CancelMatchCommand;
import com.runiverse.running_service.application.match.command.cancel.CancelMatchHandler;
import com.runiverse.running_service.application.match.common.MatchProperties;
import com.runiverse.running_service.application.match.common.MatchRoomChangedEvent;
import com.runiverse.running_service.application.match.common.RecruitmentClosePolicy;
import com.runiverse.running_service.application.match.common.RoomInfoAssembler;
import com.runiverse.running_service.application.match.port.out.MatchEventType;
import com.runiverse.running_service.application.match.port.out.MatchStreamEvent;
import com.runiverse.running_service.application.match.port.out.RoomInfo;
import com.runiverse.running_service.application.running.exception.AlreadyRunningException;
import com.runiverse.running_service.application.scheduling.command.schedule.ScheduleJobHandler;
import com.runiverse.running_service.application.user.command.onboarding.CompleteOnboardingCommand;
import com.runiverse.running_service.application.user.command.onboarding.CompleteOnboardingHandler;
import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.domain.running.player.RunningPlayer;
import com.runiverse.running_service.domain.running.player.vo.RunningPlayerStatus;
import com.runiverse.running_service.domain.running.room.RoomInvitation;
import com.runiverse.running_service.domain.running.room.RunningRoom;
import com.runiverse.running_service.domain.running.room.vo.RunningRoomInvitationStatus;
import com.runiverse.running_service.infrastructure.sse.MatchRoomMemberRegistry;
import com.runiverse.running_service.infrastructure.sse.MatchStreamRegistryAdapter;
import com.runiverse.running_service.integration_test.IntegrationTestSupport;
import com.runiverse.running_service.integration_test.fake.FakeViewUrlGenerator;
import com.runiverse.running_service.integration_test.fake.InMemoryMatchCooldownStore;
import com.runiverse.running_service.integration_test.fake.InMemoryMatchStore;
import com.runiverse.running_service.integration_test.fake.InMemoryPlayerProfileStore;
import com.runiverse.running_service.integration_test.fake.InMemoryScheduledJobStore;
import com.runiverse.running_service.integration_test.fake.InMemoryUserRelationshipStore;
import com.runiverse.running_service.integration_test.fake.RecordingMatchStreamConnection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

// 개설 → 초대 → 수락·거절 → 나가기 → 다시 초대를 페이크 저장소로 이어 본다.
// 방 갱신이 스트림까지 가는지는 BroadMatchEventHandler를 거쳐 기록 연결로 확인한다
@DisplayName("러닝 초대 통합 테스트")
public class RunningInvitationIntegrationTest extends IntegrationTestSupport {

    private static final String PASSWORD = "Password123!";
    private static final int AVG_PACE = 330;
    private static final int TARGET_DISTANCE = 7_000;
    private static final Duration CLOSE_OFFSET = Duration.ofMinutes(1);
    private static final MatchProperties MATCH_PROPERTIES = new MatchProperties(
            Duration.ofMinutes(10), Duration.ofSeconds(10), Duration.ofHours(6), 10, Duration.ofMinutes(20),
            LocalTime.of(18, 0), LocalTime.of(22, 0), Duration.ofMinutes(30), List.of(3_000, 5_000, 10_000));
    private static final InviteProperties INVITE_PROPERTIES = new InviteProperties(
            Duration.ofMinutes(10), CLOSE_OFFSET, 1_000, 42_195, 5);

    private SignUpHandler signUpHandler;
    private CompleteOnboardingHandler completeOnboardingHandler;
    private InMemoryUserRelationshipStore relationshipStore;
    private MatchRoomMemberRegistry matchRoomMemberRegistry;
    private MatchStreamRegistryAdapter matchStreamRegistry;
    private OpenInviteRoomHandler openHandler;
    private InviteFriendsHandler inviteHandler;
    private AcceptInvitationHandler acceptHandler;
    private DeclineInvitationHandler declineHandler;
    private CancelMatchHandler cancelHandler;
    private LocalDateTime startAt;
    private int userSequence;

    @BeforeEach
    void setUp() {
        signUpHandler = newSignUpHandler();
        completeOnboardingHandler = new CompleteOnboardingHandler(
                userStore, onboardingStore, onboardingStore, onboardingStore);
        relationshipStore = new InMemoryUserRelationshipStore();
        matchRoomMemberRegistry = new MatchRoomMemberRegistry();
        matchStreamRegistry = new MatchStreamRegistryAdapter();
        // 초대·수락은 지금 시각 기준 마감을 본다 — 마감이 한참 남은 방으로 연다
        startAt = LocalDateTime.now().plusHours(1).withSecond(0).withNano(0);

        InMemoryMatchStore matchStore = new InMemoryMatchStore(runningStore);
        BroadMatchEventHandler broadMatchEventHandler =
                new BroadMatchEventHandler(matchRoomMemberRegistry, matchStreamRegistry);
        // MatchEventDispatcher(AFTER_COMMIT) + Redis 왕복 + MatchEventListener를 대신한다
        ApplicationEventPublisher eventPublisher = event -> {
            if (event instanceof MatchRoomChangedEvent changed) {
                broadMatchEventHandler.handle(new BroadcastMatchEventCommand(changed.event()));
            }
        };
        // 개설의 시각 범위만 고정한다 — 핸들러가 지금 시각을 읽어 23:49 이후에 돌면 고를 시각이 없다
        InviteStartPolicy inviteStartPolicy = spy(new InviteStartPolicy(INVITE_PROPERTIES, MATCH_PROPERTIES));
        doReturn(true).when(inviteStartPolicy).isSelectable(any(), any());
        RoomInfoAssembler roomInfoAssembler = new RoomInfoAssembler(
                matchStore,                                                 // LoadMatchPlayersPort
                new InMemoryPlayerProfileStore(userStore, onboardingStore), // LoadPlayerProfilesPort
                new FakeViewUrlGenerator(),                                 // GenerateViewUrlPort
                new RecruitmentClosePolicy(MATCH_PROPERTIES, inviteStartPolicy),
                runningStore,                                               // LoadActiveApplicationPort
                matchStore,                                                 // LoadMatchRoomPort
                runningStore                                                // LoadMatchRoomDetailPort
        );
        openHandler = new OpenInviteRoomHandler(
                inviteStartPolicy, runningStore, onboardingStore, runningStore, runningStore,
                new ScheduleJobHandler(new InMemoryScheduledJobStore(), event -> {
                }), MATCH_PROPERTIES);
        inviteHandler = new InviteFriendsHandler(
                runningStore,        // LockMatchRoomPort
                runningStore,        // UpdateMatchRoomPort
                relationshipStore,   // LoadFriendIdsPort
                runningStore,        // LoadActiveUserIdsPort
                inviteStartPolicy, INVITE_PROPERTIES, roomInfoAssembler, eventPublisher);
        acceptHandler = new AcceptInvitationHandler(
                runningStore,        // LockMatchRoomPort
                runningStore,        // UpdateMatchRoomPort
                inviteStartPolicy,
                runningStore,        // ExistsActiveRunningPlayerPort
                onboardingStore,     // LoadUserAvgPacePort
                runningStore,        // CreateRunningPlayerPort
                matchStore,          // LoadMatchPlayersPort
                roomInfoAssembler, eventPublisher);
        declineHandler = new DeclineInvitationHandler(
                runningStore, runningStore, roomInfoAssembler, eventPublisher);
        // 나가기는 매칭 방과 같은 DELETE /running-matches다
        cancelHandler = new CancelMatchHandler(
                runningStore, matchStore, runningStore, matchStore, runningStore, runningStore,
                new InMemoryMatchCooldownStore(), MATCH_PROPERTIES, roomInfoAssembler, eventPublisher);
    }

    @Test
    @DisplayName("초대하면 방 참가자 스트림에 대기 중인 초대 현황이 INVITE_ROOM_UPDATED로 간다")
    void inviteShowsPendingOnRoomStream() {
        // given
        UUID host = onboardedUser();
        UUID friend = friendOf(host);
        Long roomId = open(host);
        RecordingMatchStreamConnection hostStream = connect(host, roomId);

        // when
        InviteFriendsResult result = invite(host, roomId, friend);

        // then
        assertThat(result.invitations()).containsExactly(
                new SentInvitation(friend, RunningRoomInvitationStatus.PENDING, 1));
        RoomInfo shown = lastInviteRoomOf(hostStream);
        assertThat(shown.invitations()).singleElement().satisfies(invitee -> {
            assertThat(invitee.userId()).isEqualTo(friend);
            assertThat(invitee.status()).isEqualTo(RunningRoomInvitationStatus.PENDING);
        });
        // 초대는 신청이 아니다 — 받는 사람의 running_players 행이 없다
        assertThat(runningStore.existsActive(new UserId(friend))).isFalse();
    }

    @Test
    @DisplayName("수락하면 신청·세션이 생기고 현황에서 참가자로 옮겨 가며 받은 행이 모두 수락이 된다")
    void acceptMovesInviteeIntoPlayers() {
        // given -> 두 참가자가 같은 친구를 불렀다
        UUID host = onboardedUser();
        UUID member = friendOf(host);
        UUID friend = friendOf(host);
        relationshipStore.seedFriend(member, friend);
        Long roomId = open(host);
        invite(host, roomId, member);
        accept(member, roomId);
        invite(host, roomId, friend);
        invite(member, roomId, friend);
        RecordingMatchStreamConnection hostStream = connect(host, roomId);

        // when
        acceptHandler.handle(new AcceptInvitationCommand(friend, roomId));

        // then
        RunningRoom room = runningStore.findRoom(roomId).orElseThrow();
        assertThat(room.getPlayerCount().current()).isEqualTo(3);
        assertThat(statusesOf(room, friend)).hasSize(2).containsOnly(RunningRoomInvitationStatus.ACCEPTED);
        RunningPlayer player = runningStore.loadActive(new UserId(friend)).orElseThrow();
        assertThat(player.getStatus()).isEqualTo(RunningPlayerStatus.JOINED);
        assertThat(player.getStartAt()).isEqualTo(startAt);
        RoomInfo shown = lastInviteRoomOf(hostStream);
        assertThat(shown.players()).extracting(RoomInfo.RoomPlayer::userId).contains(friend);
        assertThat(shown.invitations()).isEmpty();
    }

    @Test
    @DisplayName("거절하면 현황에 거절로 남고 다시 초대하면 대기로 되살아나며 횟수는 이어서 센다")
    void declineThenReinvite() {
        // given
        UUID host = onboardedUser();
        UUID friend = friendOf(host);
        Long roomId = open(host);
        invite(host, roomId, friend);
        RecordingMatchStreamConnection hostStream = connect(host, roomId);

        // when
        declineHandler.handle(new DeclineInvitationCommand(friend, roomId));

        // then -> 행을 지우지 않는다
        assertThat(lastInviteRoomOf(hostStream).invitations())
                .extracting(RoomInfo.RoomInvitee::status)
                .containsExactly(RunningRoomInvitationStatus.DECLINED);

        // when -> 같은 사람이 다시 부른다
        InviteFriendsResult again = invite(host, roomId, friend);

        // then
        assertThat(again.invitations()).containsExactly(
                new SentInvitation(friend, RunningRoomInvitationStatus.PENDING, 2));
        assertThat(lastInviteRoomOf(hostStream).invitations())
                .extracting(RoomInfo.RoomInvitee::status)
                .containsExactly(RunningRoomInvitationStatus.PENDING);
    }

    @Test
    @DisplayName("거절한 초대는 다시 초대받기 전에는 수락할 수 없다")
    void declinedCannotBeAccepted() {
        // given
        UUID host = onboardedUser();
        UUID friend = friendOf(host);
        Long roomId = open(host);
        invite(host, roomId, friend);
        declineHandler.handle(new DeclineInvitationCommand(friend, roomId));

        // when & then
        assertThatThrownBy(() -> accept(friend, roomId)).isInstanceOf(InvitationNotPendingException.class);
        assertThat(runningStore.findRoom(roomId).orElseThrow().getPlayerCount().current()).isEqualTo(1);
    }

    @Test
    @DisplayName("수락해 들어온 사람이 나가면 현황에 나감으로 돌아오고 다시 초대할 수 있다")
    void leaveThenReinvite() {
        // given
        UUID host = onboardedUser();
        UUID friend = friendOf(host);
        Long roomId = open(host);
        invite(host, roomId, friend);
        accept(friend, roomId);
        RecordingMatchStreamConnection hostStream = connect(host, roomId);

        // when -> 나가기는 매칭 방과 같은 경로다
        cancelHandler.handle(new CancelMatchCommand(friend));

        // then -> 초대 방은 언제 나가도 제재가 없다
        RunningRoom room = runningStore.findRoom(roomId).orElseThrow();
        assertThat(statusesOf(room, friend)).containsOnly(RunningRoomInvitationStatus.LEFT);
        assertThat(room.getPlayerCount().current()).isEqualTo(1);
        RoomInfo shown = lastInviteRoomOf(hostStream);
        assertThat(shown.invitations()).extracting(RoomInfo.RoomInvitee::status)
                .containsExactly(RunningRoomInvitationStatus.LEFT);

        // when
        InviteFriendsResult again = invite(host, roomId, friend);

        // then -> 나간 뒤 다시 들어올 수 있다
        assertThat(again.invitations()).extracting(SentInvitation::inviteCount).containsExactly(2);
        accept(friend, roomId);
        assertThat(runningStore.findRoom(roomId).orElseThrow().getPlayerCount().current()).isEqualTo(2);
    }

    @Test
    @DisplayName("초대받지 않은 사람은 방 ID를 알아도 들어갈 수 없다")
    void strangerCannotAcceptByRoomId() {
        // given
        UUID host = onboardedUser();
        UUID stranger = onboardedUser();
        Long roomId = open(host);

        // when & then
        assertThatThrownBy(() -> accept(stranger, roomId)).isInstanceOf(InvitationNotFoundException.class);
        RunningRoom room = runningStore.findRoom(roomId).orElseThrow();
        assertThat(room.getPlayerCount().current()).isEqualTo(1);
        assertThat(room.getSessions()).noneMatch(session -> session.isSameUser(new UserId(stranger)));
        assertThat(runningStore.existsActive(new UserId(stranger))).isFalse();
    }

    @Test
    @DisplayName("친구가 아닌 사람이 섞이면 아무에게도 보내지 않는다")
    void nonFriendBlocksWholeRequest() {
        // given
        UUID host = onboardedUser();
        UUID friend = friendOf(host);
        UUID stranger = onboardedUser();
        Long roomId = open(host);

        // when & then
        assertThatThrownBy(() -> invite(host, roomId, friend, stranger)).isInstanceOf(NotFriendException.class);
        assertThat(runningStore.findRoom(roomId).orElseThrow().getInvitations()).isEmpty();
    }

    @Test
    @DisplayName("다른 활성 신청이 있는 친구에게는 초대가 가지 않는다")
    void skipsFriendWithActiveApplication() {
        // given -> 친구가 자기 초대 방을 열어 활성 신청이 있다
        UUID host = onboardedUser();
        UUID busy = friendOf(host);
        UUID free = friendOf(host);
        Long roomId = open(host);
        open(busy);

        // when
        InviteFriendsResult result = invite(host, roomId, busy, free);

        // then -> 오류가 아니라 응답에서 빠진다
        assertThat(result.invitations()).extracting(SentInvitation::userId).containsExactly(free);
        assertThat(runningStore.findRoom(roomId).orElseThrow().invitationStatusOf(new UserId(busy))).isEmpty();
    }

    @Test
    @DisplayName("초대받은 뒤 다른 신청을 하면 수락이 막히고, 그 신청을 끝내면 다시 수락할 수 있다")
    void acceptBlockedByLaterApplication() {
        // given
        UUID host = onboardedUser();
        UUID friend = friendOf(host);
        Long roomId = open(host);
        invite(host, roomId, friend);
        open(friend);   // 초대는 신청이 아니라 받은 채로 다른 방을 열 수 있다

        // when & then
        assertThatThrownBy(() -> accept(friend, roomId)).isInstanceOf(AlreadyRunningException.class);
        assertThat(runningStore.findRoom(roomId).orElseThrow().invitationStatusOf(new UserId(friend)))
                .contains(RunningRoomInvitationStatus.PENDING);

        // when -> 자기 방을 나가 활성 신청이 사라졌다
        cancelHandler.handle(new CancelMatchCommand(friend));
        accept(friend, roomId);

        // then
        assertThat(runningStore.findRoom(roomId).orElseThrow().invitationStatusOf(new UserId(friend)))
                .contains(RunningRoomInvitationStatus.ACCEPTED);
    }

    @Test
    @DisplayName("자리가 다 차면 수락이 막히고 초대는 대기로 남아 누가 나가면 수락할 수 있다")
    void fullRoomKeepsInvitationPending() {
        // given -> 4명이 찬 방에도 초대는 보낼 수 있다
        UUID host = onboardedUser();
        Long roomId = open(host);
        List<UUID> members = List.of(friendOf(host), friendOf(host), friendOf(host));
        members.forEach(member -> {
            invite(host, roomId, member);
            accept(member, roomId);
        });
        UUID waiting = friendOf(host);
        invite(host, roomId, waiting);

        // when & then
        assertThatThrownBy(() -> accept(waiting, roomId)).isInstanceOf(RunningRoomFullException.class);
        assertThat(runningStore.findRoom(roomId).orElseThrow().invitationStatusOf(new UserId(waiting)))
                .contains(RunningRoomInvitationStatus.PENDING);

        // when -> 한 명이 나갔다
        cancelHandler.handle(new CancelMatchCommand(members.getFirst()));
        accept(waiting, roomId);

        // then
        assertThat(runningStore.findRoom(roomId).orElseThrow().getPlayerCount().current()).isEqualTo(4);
    }

    private Long open(UUID userId) {
        return openHandler.handle(new OpenInviteRoomCommand(userId, startAt, TARGET_DISTANCE)).runningRoomId();
    }

    private InviteFriendsResult invite(UUID inviter, Long roomId, UUID... invitees) {
        return inviteHandler.handle(new InviteFriendsCommand(inviter, roomId, Arrays.asList(invitees)));
    }

    private void accept(UUID invitee, Long roomId) {
        acceptHandler.handle(new AcceptInvitationCommand(invitee, roomId));
    }

    private UUID friendOf(UUID userId) {
        UUID friend = onboardedUser();
        relationshipStore.seedFriend(userId, friend);
        return friend;
    }

    private UUID onboardedUser() {
        userSequence++;
        UUID userId = signUpHandler.handle(new SignUpCommand(
                issueVerificationTicket("inviter" + userSequence + "@runiverse.com"), PASSWORD)).userId();
        completeOnboardingHandler.handle(new CompleteOnboardingCommand(
                userId, "초대러너" + userSequence, "MALE", LocalDate.of(1998, 5, 20),
                AVG_PACE, new BigDecimal("70.0"), new BigDecimal("175.0")));
        return userId;
    }

    private RecordingMatchStreamConnection connect(UUID userId, Long runningRoomId) {
        RecordingMatchStreamConnection connection = new RecordingMatchStreamConnection("conn-" + userId);
        matchStreamRegistry.register(new UserId(userId), connection);
        matchRoomMemberRegistry.join(new UserId(userId), runningRoomId);
        return connection;
    }

    private static List<RunningRoomInvitationStatus> statusesOf(RunningRoom room, UUID invitee) {
        return room.getInvitations().stream()
                .filter(invitation -> invitation.isFor(new UserId(invitee)))
                .map(RoomInvitation::getStatus)
                .toList();
    }

    // 초대 방 갱신은 매칭 방 이벤트가 아니라 INVITE_ROOM_UPDATED로 간다
    private static RoomInfo lastInviteRoomOf(RecordingMatchStreamConnection connection) {
        List<MatchStreamEvent> received = connection.received();
        assertThat(received).isNotEmpty();
        MatchStreamEvent last = received.getLast();
        assertThat(last.type()).isEqualTo(MatchEventType.INVITE_ROOM_UPDATED);
        return last.room();
    }
}
