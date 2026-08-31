package com.runiverse.e2e.running;

import com.runiverse.e2e.E2eTestSupport;
import com.runiverse.e2e.RunningWebSocket;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * RUNNING_FINISH는 여기서 다루지 않는다 — 종료가 GPS 원본 트랙을 S3에 올리는데
 * E2E 스택에 S3가 없어 그대로 실패한다. 종료와 결과 조회(6-1·6-2)는 아직 E2E 사각지대다.
 */
@DisplayName("배포 이미지 대상 솔로 러닝 E2E 테스트")
class SoloRunningE2eTest extends E2eTestSupport {

    private static final int POINT_COUNT = 21;
    private static final int SECONDS_PER_STEP = 2;
    private static final double METERS_PER_STEP = 5.0;
    private static final int BATCH_SIZE = 10;
    private static final double METERS_PER_LATITUDE_DEGREE = 111_320.0;
    private static final double START_LATITUDE = 37.5665;
    private static final double START_LONGITUDE = 126.9780;
    private static final int CADENCE_SPM = 170;
    private static final int PACE_SECONDS_PER_KM = 400;
    // 앱은 APP_TIME_ZONE으로 돌고 recordedAt에는 오프셋이 없다 — 호스트 시간대로 만들면 어긋난다
    private static final ZoneId APP_ZONE = ZoneId.of("Asia/Seoul");

    // 한 테스트 안의 모든 좌표가 같은 기준시각을 써야 한다 —
    // 점마다 now()를 다시 읽으면 간격이 미세하게 어긋난다. JUnit은 테스트마다 인스턴스를 새로 만든다
    private final LocalDateTime trackStartAt = LocalDateTime.now(APP_ZONE)
            .minusSeconds((long) POINT_COUNT * SECONDS_PER_STEP);

    @Test
    @DisplayName("솔로 방을 열고 좌표를 보내면 누적 거리가 진행 통지로 돌아온다")
    void soloRunningStartsAndReportsProgress() {
        // given
        TestUser user = signUpAndOnboard();
        Response opened = post("/running-rooms/solo", Map.of(), user.accessToken());
        assertThat(opened.status()).isEqualTo(201);
        long runningRoomId = opened.number("runningRoomId");

        // when
        try (RunningWebSocket socket = connectRunningWebSocket(user.accessToken())) {
            // 연결만으로는 아무것도 정해지지 않는다 — 어느 방인지는 RUNNING_START가 정한다
            socket.send("RUNNING_START", Map.of("runningRoomId", runningRoomId));
            socket.await("RUNNING_STARTED");
            sendTrack(socket);

            // then - 좌표 배치에는 ack가 없다. 누적 거리가 진행 통지로 돌아오는 것으로 확인한다
            Map<String, Object> progress = socket.await("PLAYER_RUNNING_PROGRESS_UPDATED");
            @SuppressWarnings("unchecked")
            Map<String, Object> payload = (Map<String, Object>) progress.get("data");
            assertThat(payload.get("userId")).isEqualTo(user.userId());
            assertThat((Integer) payload.get("distanceMeters")).isPositive();
            // 목표 없는 솔로 방이라 남은 거리를 잴 기준이 없다
            assertThat(payload.get("targetDistanceMeters")).isNull();
            assertThat(payload.get("paused")).isEqualTo(false);
        }
    }

    @Test
    @DisplayName("이미 진행 중인 러닝이 있으면 솔로 방을 새로 열 수 없다")
    void soloRoomIsOpenedOnlyOnce() {
        // given
        TestUser user = signUpAndOnboard();
        assertThat(post("/running-rooms/solo", Map.of(), user.accessToken()).status())
                .isEqualTo(201);
        // when
        Response second = post("/running-rooms/solo", Map.of(), user.accessToken());
        // then
        assertThat(second.status()).isEqualTo(409);
        assertThat(second.text("code")).isEqualTo("RUNNING_ALREADY_IN_PROGRESS");
    }

    @Test
    @DisplayName("온보딩을 마치지 않으면 솔로 러닝을 시작할 수 없다")
    void onboardingIsRequired() {
        // given - 가입만 하고 온보딩은 건너뛴다. 평균 페이스가 없으면 방을 열 수 없다
        String email = uniqueEmail();
        post("/auth/email/verifications", Map.of("email", email));
        Response verified = post("/auth/email/verifications/confirm",
                Map.of("email", email, "code", sentVerificationCode(email)));
        Response signedUp = post("/auth/signup", Map.of(
                "verificationTicket", verified.text("verificationTicket"),
                "password", "Password123!"));
        // when
        Response response = post("/running-rooms/solo", Map.of(), signedUp.text("accessToken"));
        // then
        assertThat(response.status()).isEqualTo(409);
        assertThat(response.text("code")).isEqualTo("ONBOARDING_NOT_COMPLETED");
    }

    @Test
    @DisplayName("남의 방 결과는 403, 없는 방은 404로 갈린다")
    void resultsAreGuarded() {
        // given
        TestUser owner = signUpAndOnboard();
        TestUser stranger = signUpAndOnboard();
        long runningRoomId = post("/running-rooms/solo", Map.of(), owner.accessToken())
                .number("runningRoomId");
        // when
        Response forbidden =
                get("/running-rooms/" + runningRoomId + "/results", stranger.accessToken());
        Response notFound = get("/running-rooms/99999999/results", stranger.accessToken());
        // then - 순서가 뒤집히면 없는 방에 403이 나가면서 방의 존재 여부가 새어 나간다
        assertThat(forbidden.status()).isEqualTo(403);
        assertThat(forbidden.text("code")).isEqualTo("NOT_ROOM_PLAYER");
        assertThat(notFound.status()).isEqualTo(404);
        assertThat(notFound.text("code")).isEqualTo("NOT_FOUND");
    }

    @Test
    @DisplayName("토큰 없이는 러닝 WebSocket 핸드셰이크가 열리지 않는다")
    void handshakeRequiresToken() {
        // when - then
        assertThatThrownBy(() -> connectRunningWebSocket(null))
                .isInstanceOf(RunningWebSocket.HandshakeFailedException.class)
                .hasMessageContaining("401");
    }

    @Test
    @DisplayName("RUNNING_START 없이 보낸 좌표와 종료는 RUNNING_NOT_STARTED로 돌아온다")
    void messagesBeforeStartAreRejected() {
        // given
        TestUser user = signUpAndOnboard();
        try (RunningWebSocket socket = connectRunningWebSocket(user.accessToken())) {
            // when - 연결만으로는 어느 방인지 정해지지 않는다
            socket.send("RUNNING_LOCATION_UPDATE", Map.of("locations", List.of(point(0))));
            // then
            assertThat(socket.awaitErrorPayload())
                    .containsEntry("code", "RUNNING_NOT_STARTED")
                    .containsEntry("sourceType", "RUNNING_LOCATION_UPDATE");
            // 방이 정해지기 전이라 유스케이스에 닿지 않는다 — S3를 부르지 않는 경로다
            socket.send("RUNNING_FINISH", Map.of("forced", true));
            assertThat(socket.awaitErrorPayload())
                    .containsEntry("code", "RUNNING_NOT_STARTED")
                    .containsEntry("sourceType", "RUNNING_FINISH");
        }
    }

    @Test
    @DisplayName("헬스체크는 되받고, 모르는 메시지는 연결을 끊지 않고 ERROR로 돌아온다")
    void unknownMessageDoesNotCloseConnection() {
        // given
        TestUser user = signUpAndOnboard();
        try (RunningWebSocket socket = connectRunningWebSocket(user.accessToken())) {
            // when - then
            socket.send("HEALTH_CHECK", Map.of());
            socket.await("HEALTH_CHECKED");

            socket.send("NOT_A_REAL_EVENT", Map.of());
            assertThat(socket.awaitErrorPayload())
                    .containsEntry("code", "UNSUPPORTED_MESSAGE_TYPE");

            // 서버가 보내는 타입을 클라가 되보내도 처리 대상이 아니다
            socket.send("RUNNING_STARTED", Map.of());
            assertThat(socket.awaitErrorPayload())
                    .containsEntry("code", "UNSUPPORTED_MESSAGE_TYPE");

            // 좌표 없는 배치는 형식 위반이다
            socket.send("RUNNING_LOCATION_UPDATE", Map.of("locations", List.of()));
            assertThat(socket.awaitErrorPayload())
                    .containsEntry("code", "INVALID_REQUEST");

            // 연결은 살아 있어야 한다 — ERROR는 끊김이 아니다
            socket.send("HEALTH_CHECK", Map.of());
            socket.await("HEALTH_CHECKED");
        }
    }

    // 좌표는 단말이 하듯 배치로 나눠 보낸다 — 한 번에 다 보내면 누적 로직이 한 번만 돈다
    private void sendTrack(RunningWebSocket socket) {
        for (int from = 0; from < POINT_COUNT; from += BATCH_SIZE) {
            List<Map<String, Object>> batch = new ArrayList<>();
            for (int sequence = from; sequence < Math.min(from + BATCH_SIZE, POINT_COUNT); sequence++) {
                batch.add(point(sequence));
            }
            socket.send("RUNNING_LOCATION_UPDATE", Map.of("locations", batch));
        }
    }

    /** 정북으로 일정 간격 걸어가는 트랙 한 점. 시작 시각은 러닝 길이만큼 과거로 잡는다. */
    private Map<String, Object> point(int sequence) {
        double latitude = START_LATITUDE
                + sequence * METERS_PER_STEP / METERS_PER_LATITUDE_DEGREE;
        Map<String, Object> point = new HashMap<>();
        point.put("sequence", sequence);
        point.put("latitude", latitude);
        point.put("longitude", START_LONGITUDE);
        point.put("altitudeMeters", 30.0);
        point.put("accuracyMeters", 5.0);
        point.put("speedMetersPerSecond", METERS_PER_STEP / SECONDS_PER_STEP);
        point.put("headingDegrees", 0.0);
        point.put("cadenceSpm", CADENCE_SPM);
        point.put("currentPaceSecondsPerKm", PACE_SECONDS_PER_KM);
        // LocalDateTime은 오프셋 없는 ISO 문자열이라야 서버가 그대로 받는다
        point.put("recordedAt", trackStartAt.plusSeconds((long) sequence * SECONDS_PER_STEP)
                .format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        return point;
    }
}
