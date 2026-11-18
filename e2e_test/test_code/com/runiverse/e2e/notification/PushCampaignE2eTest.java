package com.runiverse.e2e.notification;

import com.runiverse.e2e.E2eTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 운영 흐름 그대로 SQL로 캠페인·명단을 넣고 관리자 API를 부른다.
 * 푸시는 local 프로필의 발송 어댑터가 로그로만 남겨, 받는 사람별 결과는 앱 로그에서 확인한다.
 */
@DisplayName("배포 이미지 대상 공지 푸시 E2E 테스트")
class PushCampaignE2eTest extends E2eTestSupport {

    // .env.test의 ADMIN_KEY와 같아야 한다
    private static final String ADMIN_KEY = "e2e-admin-key";
    private static final int LOG_LOOKUP_RETRIES = 50;

    @Test
    @DisplayName("키를 실어 부르면 명단에 한 번 보내고, 두 번째는 409이며, 키가 없거나 틀리면 401이다")
    void sendsCampaignOnceWithAdminKey() {
        // given - 철수는 기기를 등록했고 영희는 등록하지 않았다
        TestUser chulsu = signUpAndOnboard();
        TestUser younghee = signUpAndOnboard();
        assertThat(post("/users/me/devices",
                Map.of("deviceId", UUID.randomUUID().toString(), "pushToken", "token-1"),
                chulsu.accessToken()).status()).isEqualTo(204);
        String title = "점검 안내 " + UUID.randomUUID();
        long campaignId = insertCampaign(title, "11월 20일 02시부터 30분간 점검해요");
        executeSql(String.format("""
                INSERT INTO push_campaign_recipients (push_campaign_id, user_id, created_at)
                SELECT %d, user_id, now() FROM users WHERE user_id IN ('%s', '%s')""",
                campaignId, chulsu.userId(), younghee.userId()));

        // when & then - 키가 없거나 틀리면 앱 버전 헤더 없이도 401이고 보내지 않는다
        assertThat(send(campaignId, null)).isEqualTo(new Response(401, Map.of(
                "code", "INVALID_ADMIN_KEY", "message", "관리자 키가 올바르지 않습니다.")));
        assertThat(send(campaignId, "wrong-key").status()).isEqualTo(401);
        assertThat(statusOf(campaignId)).isEqualTo("DRAFT");

        // when & then - 키를 실으면 204이고 다시 보낼 수 없는 상태가 된다
        assertThat(send(campaignId, ADMIN_KEY).status()).isEqualTo(204);
        assertThat(statusOf(campaignId)).isEqualTo("COMPLETED");

        // then - 명단을 실제 DB에서 읽어 한 명씩 보낸다. 기기가 없는 영희는 건너뛴다
        awaitLog("[기기] 푸시 발송 건너뜀: 로컬 프로필 - targetUserId=%s, pushType=NOTICE, title=%s"
                .formatted(chulsu.userId(), title));
        awaitLog("[기기] 푸시 발송 건너뜀: 켜진 기기 없음 - targetUserId=%s, pushType=NOTICE"
                .formatted(younghee.userId()));
        awaitLog("[알림] 공지 발송 성공 - pushCampaignId=%d, recipientCount=2, exceptionCount=0"
                .formatted(campaignId));

        // when & then - 두 번째 호출은 다시 보내지 않는다
        assertThat(send(campaignId, ADMIN_KEY)).isEqualTo(new Response(409, Map.of(
                "code", "PUSH_CAMPAIGN_ALREADY_SENT", "message", "이미 보낸 푸시 캠페인입니다.")));
    }

    @Test
    @DisplayName("없는 캠페인이나 없는 관리자 경로는 404이고, 키가 없으면 경로와 무관하게 401이다")
    void unknownCampaignOrPathIs404() {
        // when & then
        assertThat(send(Long.MAX_VALUE, ADMIN_KEY)).isEqualTo(new Response(404, Map.of(
                "code", "NOT_FOUND", "message", "요청한 리소스를 찾을 수 없습니다.")));
        assertThat(post("/admin/unknown", null, null, Map.of("X-Admin-Key", ADMIN_KEY)).status()).isEqualTo(404);
        assertThat(post("/admin/unknown", null, null, Map.of()).status()).isEqualTo(401);
        // 인코딩한 경로도 컨트롤러가 디코딩해 찾으므로 키 검사를 건너뛰지 못한다.
        // 앱 버전 헤더를 실어 버전 검사(원래 주소로 거른다)를 통과시킨 뒤에도 키 필터가 막는지 본다
        assertThat(post("/%61dmin/push-campaigns/1/send", null, null,
                Map.of("X-App-Platform", "ANDROID", "X-App-Version", "1.2.0")).text("code"))
                .isEqualTo("INVALID_ADMIN_KEY");
    }

    @Test
    @DisplayName("문구가 공백뿐이면 500으로 거절하고 캠페인은 DRAFT로 남는다")
    void blankTextKeepsDraft() {
        // given - 길이 CHECK는 공백 문구를 통과시킨다
        long campaignId = insertCampaign("   ", "본문");

        // when & then
        assertThat(send(campaignId, ADMIN_KEY).status()).isEqualTo(500);
        assertThat(statusOf(campaignId)).isEqualTo("DRAFT");
    }

    // 앱 버전 헤더 없이 관리자 키만 싣는다 — 운영자가 터미널에서 부르는 모양이다
    private Response send(long campaignId, String adminKey) {
        Map<String, String> headers = adminKey == null ? Map.of() : Map.of("X-Admin-Key", adminKey);
        return post("/admin/push-campaigns/%d/send".formatted(campaignId), null, null, headers);
    }

    private long insertCampaign(String title, String body) {
        List<String> rows = querySql(String.format("""
                INSERT INTO push_campaigns (type, status, title, body, created_at)
                VALUES ('NOTICE', 'DRAFT', '%s', '%s', now())
                RETURNING push_campaign_id""", title, body));
        return rows.stream()
                .filter(row -> row.matches("\\d+"))
                .map(Long::parseLong)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("캠페인 ID를 받지 못했습니다: " + rows));
    }

    private String statusOf(long campaignId) {
        return querySql("SELECT status FROM push_campaigns WHERE push_campaign_id = " + campaignId).getFirst();
    }

    // 푸시는 커밋 뒤 발송 스레드가 보내 응답보다 늦게 찍힌다
    private void awaitLog(String expected) {
        for (int attempt = 0; attempt < LOG_LOOKUP_RETRIES; attempt++) {
            if (containerLogs().contains(expected)) {
                return;
            }
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
        }
        throw new AssertionError("앱 로그에 없다: " + expected);
    }
}
