package com.runiverse.running_service.integration_test.running;

import com.runiverse.running_service.application.auth.command.signup.SignUpCommand;
import com.runiverse.running_service.application.auth.command.signup.SignUpHandler;
import com.runiverse.running_service.application.match.common.MatchProperties;
import com.runiverse.running_service.application.running.command.finish.FinishRunningHandler;
import com.runiverse.running_service.application.running.command.forcefinish.ForceFinishRunningRoomHandler;
import com.runiverse.running_service.application.running.command.forcefinish.ForceFinishProperties;
import com.runiverse.running_service.application.running.command.forcefinish.ForceFinishRunnerDispatcher;
import com.runiverse.running_service.application.running.command.forcefinish.ForceFinishSteps;
import com.runiverse.running_service.application.running.command.forcefinish.RunningForceFinishExecutor;
import com.runiverse.running_service.application.running.command.forcefinish.RunningForceFinishListener;
import com.runiverse.running_service.application.running.command.forcefinish.RunningForceFinishRequestedEvent;
import com.runiverse.running_service.application.running.command.location.UpdateRunningFinishJudge;
import com.runiverse.running_service.application.running.command.location.UpdateRunningLocationCommand;
import com.runiverse.running_service.application.running.command.location.UpdateRunningLocationHandler;
import com.runiverse.running_service.application.running.common.LiveRunningStatusChanger;
import com.runiverse.running_service.application.running.common.RunningFinishProperties;
import com.runiverse.running_service.application.running.common.RunningFinisher;
import com.runiverse.running_service.application.running.common.RunningFinishSteps;
import com.runiverse.running_service.application.running.port.out.TrackPoint;
import com.runiverse.running_service.application.scheduling.command.run.RunScheduledJobCommand;
import com.runiverse.running_service.application.scheduling.command.run.RunScheduledJobHandler;
import com.runiverse.running_service.application.scheduling.command.schedule.ScheduleJobHandler;
import com.runiverse.running_service.application.user.command.onboarding.CompleteOnboardingCommand;
import com.runiverse.running_service.application.user.command.onboarding.CompleteOnboardingHandler;
import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.domain.running.player.RunningPlayer;
import com.runiverse.running_service.domain.running.player.vo.RunningPlayerId;
import com.runiverse.running_service.domain.running.player.vo.RunningPlayerStatus;
import com.runiverse.running_service.domain.running.room.RoomSession;
import com.runiverse.running_service.domain.running.room.RunningRoom.SessionDraft;
import com.runiverse.running_service.domain.running.room.RunningRoom;
import com.runiverse.running_service.domain.running.room.vo.RunningRoomStatus;
import com.runiverse.running_service.domain.running.room.vo.RunningRoomType;
import com.runiverse.running_service.domain.scheduling.ScheduledJob;
import com.runiverse.running_service.domain.scheduling.vo.ScheduledJobId;
import com.runiverse.running_service.domain.scheduling.vo.ScheduledJobType;
import com.runiverse.running_service.infrastructure.metrics.running.RunningMetricAdapter;
import com.runiverse.running_service.integration_test.IntegrationTestSupport;
import com.runiverse.running_service.integration_test.fake.InMemoryScheduledJobStore;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.runiverse.running_service.support.TrackFilterFixtures.DEFAULT_PROPERTIES;
import static org.assertj.core.api.Assertions.assertThat;

// 예약 발화 → 남은 참가자 확정 → 방 종료까지를 실제 클래스로 잇는다.
// 강제 종료와 러닝 종료 두 핸들러가 같은 방을 놓고 맞물리는 구간이라
// 단위 테스트(러닝 종료를 목으로 막는다)로는 드러나지 않는 것들이 여기서 걸린다
@DisplayName("러닝 강제 종료 통합 테스트")
public class ForceFinishRunningIntegrationTest extends IntegrationTestSupport {

    private static final String PASSWORD = "Password123!";
    private static final int AVG_PACE = 330;                     // 5분 30초/km
    private static final int TARGET_DISTANCE = 5_000;
    private static final BigDecimal WEIGHT = new BigDecimal("70.0");
    private static final LocalDateTime TRACK_START = LocalDateTime.of(2026, 9, 8, 19, 0, 0);
    // TrackDistance와 같은 지구 반경 — 어긋나면 의도한 거리와 측정 거리가 벌어진다
    private static final double METERS_PER_DEGREE = Math.toRadians(1) * 6_371_008.8;
    // 강제 종료가 거두는 사람은 목표를 못 채운 채 아직 뛰고 있던 사람이다 — 목표를 채우면
    // 좌표 배치가 그 자리에서 끝내 버린다. 초당 2.5m라 약 4,500m(목표의 90%)로 제재선(80%) 위다
    private static final int RUNNING_POINTS = 1_801;
    // 약 3,000m(목표의 60%) — 제재선 아래
    private static final int SHORT_POINTS = 1_201;
    // 약 5,250m — 좌표 배치만으로 목표를 넘겨 강제 종료 전에 이미 끝난다
    private static final int COMPLETING_POINTS = 2_101;

    // 운영 설정과 같은 값
    private static final Duration FORCE_FINISH_OFFSET = Duration.ofHours(6);
    private static final Duration MATCH_COOLDOWN = Duration.ofMinutes(20);
    private static final Duration RUNNING_COOLDOWN = Duration.ofMinutes(20);
    private static final MatchProperties MATCH_PROPERTIES = new MatchProperties(
            Duration.ofMinutes(10), Duration.ofMinutes(5), Duration.ofSeconds(10), FORCE_FINISH_OFFSET,
            10, MATCH_COOLDOWN,
            LocalTime.of(18, 0), LocalTime.of(22, 0), Duration.ofMinutes(60), List.of(3_000, 5_000, 10_000));
    private static final RunningFinishProperties FINISH_PROPERTIES = new RunningFinishProperties(
            0.8, 10, 100, 60, 3.0, RUNNING_COOLDOWN);
    // 고정 시각(TRACK_START)의 방이라 운영 값(7시간)이면 이미 포기 기간이다 — 포기는 따로 본다
    private static final ForceFinishProperties FORCE_FINISH_PROPERTIES = new ForceFinishProperties(
            Duration.ofMinutes(1), Duration.ofDays(3_650));

    private InMemoryScheduledJobStore scheduledJobStore;
    private SignUpHandler signUpHandler;
    private CompleteOnboardingHandler completeOnboardingHandler;
    private UpdateRunningLocationHandler updateRunningLocationHandler;
    private ScheduleJobHandler scheduleJobHandler;
    private RunScheduledJobHandler runScheduledJobHandler;
    // 제재가 실제로 걸렸는지 보려면 발급 자체를 잡아야 한다 — 근거(status)와 별개 축이다
    private Map<UUID, Duration> cooldowns;
    private List<RunningForceFinishRequestedEvent> forceFinishRequests;
    private RunningForceFinishListener forceFinishListener;

    @BeforeEach
    void setUp() {
        scheduledJobStore = new InMemoryScheduledJobStore();
        cooldowns = new HashMap<>();
        forceFinishRequests = new ArrayList<>();
        signUpHandler = newSignUpHandler();
        completeOnboardingHandler = new CompleteOnboardingHandler(
                userStore,        // LoadUserByIdPort
                onboardingStore,  // ExistsOnboardingPort
                onboardingStore,  // CheckNicknameDuplicatePort
                onboardingStore   // SaveOnboardingPort
        );
        RunningFinisher runningFinisher = new RunningFinisher(
                finishLeaseStore, // FinishLeasePort
                new RunningFinishSteps(
                        runningStore, // LockRunningRoomPort
                        runningStore, // LockRunningPlayerPort
                        runningTrackStore, // LoadRunningTrackPort
                        onboardingStore, // LoadUserWeightPort
                        runningRecordStore, // CreateRunningRecordPort
                        runningStore, // UpdateRunningPlayerPort
                        runningTrackStore, // DeleteRunningTrackPort
                        runningStore, // ExistsRunningPlayerPort
                        runningStore, // UpdateRunningRoomPort
                        this::recordCooldown, // StartMatchCooldownPort
                        runningRecordStore, // ExistsRunningRecordPort
                        runningRecordStore, // LoadRecentRunningPacesPort
                        onboardingStore, // UpdateUserAvgPacePort
                        new LiveRunningStatusChanger( liveRunningStatusStore, runningDistanceStore, runningProgressPublisher), // LiveRunningStatusChanger
                        newRunningComboArchiver(), // RunningComboArchiver
                        runningComboStore, // MarkRunningComboFinishedPort
                        event -> { if (event instanceof RunningForceFinishRequestedEvent request) { forceFinishRequests.add(request); } }, // ApplicationEventPublisher
                        FINISH_PROPERTIES, // RunningFinishProperties
                        DEFAULT_PROPERTIES, // TrackFilterProperties
                        new RunningMetricAdapter(new SimpleMeterRegistry())), // RecordRunningMetricPort
                gpsTrackUploader, // SaveGpsTrackPort
                weatherProvider, // LoadWeatherPort
                FINISH_PROPERTIES, // RunningFinishProperties
                new RunningMetricAdapter(new SimpleMeterRegistry()));
        updateRunningLocationHandler = new UpdateRunningLocationHandler(
                runningTrackStore,       // AppendRunningTrackPort
                runningDistanceStore,    // LoadRunningDistancePort
                runningDistanceStore,    // SaveRunningDistancePort
                liveRunningStatusStore,   // ChangeLiveRunningStatusPort
                liveRunningStatusStore,   // LoadLiveRunningStatusPort
                runningProgressPublisher, // PublishRunningProgressPort
                newUpdateRunningComboJudge(),
                new UpdateRunningFinishJudge(runningFinisher),
                DEFAULT_PROPERTIES
        );
        FinishRunningHandler finishRunningHandler = new FinishRunningHandler(runningFinisher);
        ForceFinishRunningRoomHandler forceFinishRunningRoomHandler =
                new ForceFinishRunningRoomHandler(
                        runningStore,         // LoadRunningRoomPort
                        runningStore,         // LoadRoomPlayerPort
                        new ForceFinishSteps(
                                runningStore,         // LockRunningPlayerPort
                                runningStore,         // LockRunningRoomPort
                                runningStore,         // LoadRoomPlayerPort
                                runningStore,         // UpdateRunningRoomPort
                                runningStore,         // UpdateRunningPlayerPort
                                this::recordCooldown, // StartMatchCooldownPort
                                runningRecordStore,   // ExistsRunningRecordPort
                                MATCH_PROPERTIES,
                                newRunningComboArchiver()),
                        // 워커는 그 자리에서 돈다 — 스레드를 띄우면 단언 시점에 끝났는지 알 수 없다
                        new ForceFinishRunnerDispatcher(Runnable::run, finishRunningHandler),
                        MATCH_PROPERTIES,
                        FORCE_FINISH_PROPERTIES
                );
        forceFinishListener = new RunningForceFinishListener(forceFinishRunningRoomHandler);
        runScheduledJobHandler = new RunScheduledJobHandler(
                scheduledJobStore, scheduledJobStore, event -> {
                },
                List.of(new RunningForceFinishExecutor(forceFinishRunningRoomHandler)));
        // 타이머 등록과 전파는 이 테스트의 주제가 아니다 — 발화는 아래에서 직접 부른다
        scheduleJobHandler = new ScheduleJobHandler(scheduledJobStore, event -> {
        });
    }

    @Test
    @DisplayName("뛰던 사람이 있었으면 예약을 1분 뒤로 다시 걸고, 다음 알람이 다 끝난 것을 보고 완료로 적는다 (D-83)")
    void reschedulesThenCompletesOnNextAlarm() {
        // given
        UUID runner = onboardedUser("runner@runiverse.com", "러너킴");
        UUID noShow = onboardedUser("noshow@runiverse.com", "안온사람");
        long roomId = givenStartedMatchRoom(runner, noShow);
        runFor(runner, roomId, RUNNING_POINTS);
        schedule(roomId);

        // when -> 첫 알람: 미출석 이탈 + 러너는 워커가 끝낸다
        fire(roomId);

        // then -> 워커가 끝냈는지 알람은 모른다 — 완료로 적지 않고 1분 뒤로 다시 건다
        assertThat(storedRoom(roomId).getStatus()).isEqualTo(RunningRoomStatus.FINISHED);
        assertThat(storedJob(roomId).isSent()).isFalse();
        assertThat(storedJob(roomId).getExecuteAt()).isAfter(LocalDateTime.now());

        // when -> 1분 뒤 알람
        makeDue(roomId);
        fire(roomId);

        // then -> 다 끝난 것을 보고 완료로 적는다. 기록은 하나다
        assertThat(storedJob(roomId).isSent()).isTrue();
        assertThat(runningRecordStore.size()).isOne();
    }

    @Test
    @DisplayName("러너가 직접 끝내는 중이면 그 러너만 남기고, 다음 알람이 끝낸다")
    void leavesRunnerFinishingThemselvesToNextAlarm() {
        // given -> 6시간 정각에 러너가 직접 종료를 눌러 끝내기 임대를 쥐고 봉인 중이다
        UUID runner = onboardedUser("runner@runiverse.com", "러너킴");
        UUID noShow = onboardedUser("noshow@runiverse.com", "안온사람");
        long roomId = givenStartedMatchRoom(runner, noShow);
        runFor(runner, roomId, RUNNING_POINTS);
        schedule(roomId);
        finishLeaseStore.hold(roomId, new UserId(runner));

        // when
        fire(roomId);

        // then -> 방 전체가 되돌아가지 않는다 — 미출석 처리는 남고 러너만 그대로다
        assertThat(storedPlayer(roomId, noShow).getStatus())
                .isEqualTo(RunningPlayerStatus.MATCHED_LEFT_PENALTY);
        assertThat(storedPlayer(roomId, runner).getStatus()).isEqualTo(RunningPlayerStatus.RUNNING);
        assertThat(storedJob(roomId).isSent()).isFalse();

        // when -> 본인 종료가 실패해 임대를 놓았고, 1분 뒤 알람이 다시 넘긴다
        finishLeaseStore.release(roomId, new UserId(runner), "held-by-other");
        makeDue(roomId);
        fire(roomId);
        makeDue(roomId);
        fire(roomId);

        // then
        assertThat(storedPlayer(roomId, runner).isActive()).isFalse();
        assertThat(storedRoom(roomId).getStatus()).isEqualTo(RunningRoomStatus.FINISHED);
        assertThat(storedJob(roomId).isSent()).isTrue();
    }

    @Test
    @DisplayName("뛴 사람은 러닝 종료로, 안 나타난 사람은 확정 후 이탈로 함께 닫는다")
    void closesRunnerAndNoShowInOneRoom() {
        // given -> 2인 확정 방에서 A만 붙어 목표 직전까지 뛰었고 B는 한 번도 오지 않았다
        UUID runner = onboardedUser("runner@runiverse.com", "러너킴");
        UUID noShow = onboardedUser("noshow@runiverse.com", "안온사람");
        long roomId = givenStartedMatchRoom(runner, noShow);
        runFor(runner, roomId, RUNNING_POINTS);
        schedule(roomId);

        // when
        fire(roomId);

        // then -> 뛴 사람은 평소 종료와 똑같다 — 거리 비율로 판정하고 기록도 남긴다
        assertThat(storedPlayer(roomId, runner).getStatus())
                .isEqualTo(RunningPlayerStatus.RUNNING_LEFT_NO_PENALTY);
        assertThat(runningRecordStore.find(roomId, new UserId(runner))).isPresent();
        // 안 나타난 사람은 RUNNING을 거치지 않았으므로 조기 종료가 아니라 확정 후 이탈이다
        assertThat(storedPlayer(roomId, noShow).getStatus())
                .isEqualTo(RunningPlayerStatus.MATCHED_LEFT_PENALTY);
        assertThat(runningRecordStore.find(roomId, new UserId(noShow))).isEmpty();
    }

    @Test
    @DisplayName("제재는 안 나타난 사람에게만, 이탈 쪽 쿨다운으로 걸린다")
    void penalizesOnlyNoShowWithMatchCooldown() {
        // given -> 제재선(80%)을 넘겨 뛴 A는 제재 대상이 아니다
        UUID runner = onboardedUser("runner@runiverse.com", "러너킴");
        UUID noShow = onboardedUser("noshow@runiverse.com", "안온사람");
        long roomId = givenStartedMatchRoom(runner, noShow);
        runFor(runner, roomId, RUNNING_POINTS);
        schedule(roomId);

        // when
        fire(roomId);

        // then -> 남는 상태가 MATCHED_LEFT_*라 조기 종료(running-finish)가 아니라 이탈(match) 값이다
        assertThat(cooldowns).containsOnlyKeys(noShow);
        assertThat(cooldowns.get(noShow)).isEqualTo(MATCH_COOLDOWN);
    }

    @Test
    @DisplayName("뛴 사람이 있으면 방은 완료로 닫히고 자리는 전부 비워진다")
    void closesRoomAsFinishedWhenSomeoneRan() {
        // given
        UUID runner = onboardedUser("runner@runiverse.com", "러너킴");
        UUID noShow = onboardedUser("noshow@runiverse.com", "안온사람");
        long roomId = givenStartedMatchRoom(runner, noShow);
        runFor(runner, roomId, RUNNING_POINTS);
        schedule(roomId);

        // when
        fire(roomId);

        // then -> 남길 기록이 있으니 완료다. 강제 종료가 들고 있던 방 인스턴스로 덮어썼다면
        //         러닝 종료가 만든 이 전이가 사라진다
        RunningRoom room = storedRoom(roomId);
        assertThat(room.getStatus()).isEqualTo(RunningRoomStatus.FINISHED);
        assertThat(room.getCloseAt()).isPresent();
        // 강제 종료는 인원을 줄이지 않는다 — 자리를 비우는 것과 인원을 줄이는 것은 다르다
        assertThat(room.getPlayerCount().current()).isEqualTo(2);
        assertThat(room.getSessions()).extracting(RoomSession::isConnected)
                .containsOnly(false);
        assertThat(runningTrackStore.isEmpty(roomId, new UserId(runner))).isTrue();
    }

    @Test
    @DisplayName("아무도 나타나지 않은 방은 남길 기록이 없어 취소로 닫힌다")
    void cancelsRoomNobodyRanIn() {
        // given -> 둘 다 채널에 붙지 않았다. 방을 닫아 줄 러닝 종료가 아예 없다
        UUID first = onboardedUser("first@runiverse.com", "안온사람1");
        UUID second = onboardedUser("second@runiverse.com", "안온사람2");
        long roomId = givenStartedMatchRoom(first, second);
        schedule(roomId);

        // when
        fire(roomId);

        // then
        RunningRoom room = storedRoom(roomId);
        assertThat(room.getStatus()).isEqualTo(RunningRoomStatus.CANCELLED);
        assertThat(room.getCloseAt()).isPresent();
        assertThat(runningRecordStore.size()).isZero();
        assertThat(storedPlayer(roomId, first).getStatus())
                .isEqualTo(RunningPlayerStatus.MATCHED_LEFT_PENALTY);
        assertThat(storedPlayer(roomId, second).getStatus())
                .isEqualTo(RunningPlayerStatus.MATCHED_LEFT_PENALTY);
    }

    @Test
    @DisplayName("1인 확정 방의 미출석은 제재 없이 닫는다")
    void doesNotPenalizeNoShowInSinglePlayerRoom() {
        // given -> 안 나타나도 곤란해지는 상대가 없다. 시작 전 이탈 면제와 같은 기준이다
        UUID alone = onboardedUser("alone@runiverse.com", "혼자온사람");
        long roomId = givenStartedMatchRoom(alone);
        schedule(roomId);

        // when
        fire(roomId);

        // then
        assertThat(storedPlayer(roomId, alone).getStatus())
                .isEqualTo(RunningPlayerStatus.MATCHED_LEFT_NO_PENALTY);
        assertThat(cooldowns).isEmpty();
        assertThat(storedRoom(roomId).getStatus()).isEqualTo(RunningRoomStatus.CANCELLED);
    }

    @Test
    @DisplayName("강제 종료가 끝나면 활성 신청이 남지 않는다")
    void leavesNoActiveApplication() {
        // given -> 방이 열린 채로 남으면 신청도 활성으로 남아 다음 러닝이 영영 막힌다.
        //          이 예약을 만든 이유 자체다
        UUID runner = onboardedUser("runner@runiverse.com", "러너킴");
        UUID noShow = onboardedUser("noshow@runiverse.com", "안온사람");
        long roomId = givenStartedMatchRoom(runner, noShow);
        runFor(runner, roomId, RUNNING_POINTS);
        schedule(roomId);

        // when
        fire(roomId);

        // then -> 뛴 사람도 안 나타난 사람도 신청이 끝나 있다
        assertThat(runningStore.existsActive(new UserId(runner))).isFalse();
        assertThat(runningStore.existsActive(new UserId(noShow))).isFalse();
    }

    @Test
    @DisplayName("예약이 두 번 깨도 한 번만 실행된다")
    void firesOnlyOnce() {
        // given -> 인스턴스 여럿이 같은 예약을 타이머로 들고 있다.
        //          두 번째가 그대로 지나가면 이미 닫힌 참가자에 다시 손을 대 도메인 예외가 난다
        UUID runner = onboardedUser("runner@runiverse.com", "러너킴");
        UUID noShow = onboardedUser("noshow@runiverse.com", "안온사람");
        long roomId = givenStartedMatchRoom(runner, noShow);
        runFor(runner, roomId, RUNNING_POINTS);
        schedule(roomId);

        // when
        fire(roomId);
        fire(roomId);

        // then -> 기록이 두 번 쌓이지 않고 상태도 그대로다
        assertThat(runningRecordStore.size()).isOne();
        assertThat(storedRoom(roomId).getStatus()).isEqualTo(RunningRoomStatus.FINISHED);
        assertThat(cooldowns).containsOnlyKeys(noShow);
    }

    @Test
    @DisplayName("제재선에 못 미친 채 뛰던 사람은 조기 종료 제재를 받는다")
    void penalizesShortRunnerAsEarlyLeave() {
        // given -> 뛰긴 했지만 목표의 60%에서 멈췄다
        UUID runner = onboardedUser("runner@runiverse.com", "러너킴");
        UUID teammate = onboardedUser("teammate@runiverse.com", "같이뛴사람");
        long roomId = givenStartedMatchRoom(runner, teammate);
        runFor(runner, roomId, SHORT_POINTS);
        runFor(teammate, roomId, RUNNING_POINTS);
        schedule(roomId);

        // when
        fire(roomId);

        // then -> 붙었으니 미출석(MATCHED_LEFT_*)이 아니라 러닝 종료 규칙의 조기 종료다
        assertThat(storedPlayer(roomId, runner).getStatus())
                .isEqualTo(RunningPlayerStatus.RUNNING_LEFT_PENALTY);
        assertThat(storedPlayer(roomId, teammate).getStatus())
                .isEqualTo(RunningPlayerStatus.RUNNING_LEFT_NO_PENALTY);
        assertThat(cooldowns).containsOnlyKeys(runner);
        assertThat(cooldowns.get(runner)).isEqualTo(RUNNING_COOLDOWN);
    }

    @Test
    @DisplayName("목표를 채워 이미 끝난 사람은 강제 종료가 다시 건드리지 않는다")
    void leavesAutoFinishedRunnerAlone() {
        // given -> A는 좌표 배치로 목표를 채워 그 자리에서 끝났고, B는 한 번도 오지 않았다
        UUID runner = onboardedUser("runner@runiverse.com", "러너킴");
        UUID noShow = onboardedUser("noshow@runiverse.com", "안온사람");
        long roomId = givenStartedMatchRoom(runner, noShow);
        runFor(runner, roomId, COMPLETING_POINTS);
        schedule(roomId);

        // when
        fire(roomId);

        // then -> A의 완주와 기록은 그대로고, 강제 종료는 B만 닫는다
        assertThat(storedPlayer(roomId, runner).getStatus())
                .isEqualTo(RunningPlayerStatus.COMPLETED);
        assertThat(runningRecordStore.size()).isOne();
        assertThat(storedPlayer(roomId, noShow).getStatus())
                .isEqualTo(RunningPlayerStatus.MATCHED_LEFT_PENALTY);
        assertThat(cooldowns).containsOnlyKeys(noShow);
        assertThat(storedRoom(roomId).getStatus()).isEqualTo(RunningRoomStatus.FINISHED);
    }

    @Test
    @DisplayName("뛴 사람이 모두 끝나 방이 닫히면 남은 미출석자를 예약 시각을 기다리지 않고 닫는다")
    void settlesNoShowRightAfterRoomCloses() {
        // given -> A가 목표를 채워 방이 닫혔고, B는 한 번도 오지 않아 활성 신청이 남았다
        UUID runner = onboardedUser("runner@runiverse.com", "러너킴");
        UUID noShow = onboardedUser("noshow@runiverse.com", "안온사람");
        long roomId = givenStartedMatchRoom(runner, noShow);
        runFor(runner, roomId, COMPLETING_POINTS);
        assertThat(storedRoom(roomId).getStatus()).isEqualTo(RunningRoomStatus.FINISHED);

        // when -> 커밋 뒤 전달
        forceFinishRequests.forEach(forceFinishListener::forceFinish);

        // then
        assertThat(forceFinishRequests).extracting(RunningForceFinishRequestedEvent::runningRoomId)
                .containsExactly(roomId);
        assertThat(storedPlayer(roomId, noShow).getStatus())
                .isEqualTo(RunningPlayerStatus.MATCHED_LEFT_PENALTY);
        assertThat(runningStore.existsActive(new UserId(noShow))).isFalse();
        assertThat(cooldowns).containsOnlyKeys(noShow);
    }

    private void recordCooldown(UserId userId, Duration cooldown) {
        cooldowns.put(userId.value(), cooldown);
    }

    private UUID onboardedUser(String email, String nickname) {
        UUID userId = signUpHandler.handle(
                new SignUpCommand(issueVerificationTicket(email), PASSWORD)).userId();
        completeOnboardingHandler.handle(new CompleteOnboardingCommand(
                userId, nickname, "MALE", LocalDate.of(1998, 5, 20),
                AVG_PACE, WEIGHT, new BigDecimal("175.0")));
        return userId;
    }

    // 강제 종료가 도는 시점의 방 — 시작 스케줄러가 이미 STARTED로 올려두었고
    // 시작 시각은 유예만큼 지나 있다. 붙은 사람만 RUNNING이다
    private long givenStartedMatchRoom(UUID... members) {
        LocalDateTime startAt = LocalDateTime.now().minus(FORCE_FINISH_OFFSET);
        List<SessionDraft> sessions = new ArrayList<>();
        for (UUID member : members) {
            RunningPlayer saved = runningStore.create(RunningPlayer.builder()
                    .userId(member)
                    .status(RunningPlayerStatus.JOINED)
                    .avgPace(AVG_PACE)
                    .targetDistance(TARGET_DISTANCE)
                    .startAt(startAt)
                    .build());
            sessions.add(new SessionDraft(new UserId(member),
                    saved.getRunningPlayerId().orElseThrow(), 0, true));
        }
        RunningRoom saved = runningStore.create(RunningRoom.builder()
                .type(RunningRoomType.MATCH)
                .status(RunningRoomStatus.STARTED)
                .startAt(startAt)
                .targetDistance(TARGET_DISTANCE)
                .avgPace(AVG_PACE)
                .currentPlayerCount(members.length)
                .maxPlayerCount(4)
                .sessions(sessions)
                .build());
        return saved.getRunningRoomId().orElseThrow().value();
    }

    // 채널에 붙어 좌표를 밀어 넣은 사람 = RUNNING. 안 부르면 JOINED로 남아 미출석이 된다
    private void runFor(UUID userId, long roomId, int count) {
        RunningPlayer player = storedPlayer(roomId, userId);
        player.start();
        runningStore.update(player);
        List<TrackPoint> points = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            points.add(new TrackPoint(i, 37.5 + i * 2.5 / METERS_PER_DEGREE, 127.0,
                    null, 5.0, null, null, 168, null, TRACK_START.plusSeconds(i), 0));
        }
        updateRunningLocationHandler.handle(
                new UpdateRunningLocationCommand(userId, roomId, TARGET_DISTANCE, points));
    }

    private void schedule(long roomId) {
        scheduleJobHandler.schedule(ScheduledJobType.RUNNING_FORCE_FINISH, roomId,
                storedRoom(roomId).getStartAt().plus(FORCE_FINISH_OFFSET));
    }

    // 예약 실행기는 ID로 부른다 — 타이머가 들고 있던 값이다
    private void fire(long roomId) {
        ScheduledJob job = scheduledJobStore
                .findBy(ScheduledJobType.RUNNING_FORCE_FINISH, roomId)
                .orElseThrow();
        runScheduledJobHandler.handle(new RunScheduledJobCommand(
                job.getScheduledJobId().map(ScheduledJobId::value).orElseThrow()));
    }

    private ScheduledJob storedJob(long roomId) {
        return scheduledJobStore.findBy(ScheduledJobType.RUNNING_FORCE_FINISH, roomId).orElseThrow();
    }

    // 1분을 기다리지 않는다 — 다시 건 시각을 지난 것으로 돌려 다음 알람을 흉내 낸다
    private void makeDue(long roomId) {
        ScheduledJob job = storedJob(roomId);
        job.reschedule(LocalDateTime.now().minusSeconds(1));
        scheduledJobStore.update(job);
    }

    private RunningRoom storedRoom(long roomId) {
        return runningStore.findRoom(roomId).orElseThrow();
    }

    private RunningPlayer storedPlayer(long roomId, UUID userId) {
        RunningPlayerId playerId = storedRoom(roomId).getSessions().stream()
                .filter(session -> session.isSameUser(new UserId(userId)))
                .findFirst()
                .orElseThrow()
                .getRunningPlayerId();
        return runningStore.findPlayer(playerId.value()).orElseThrow();
    }
}
