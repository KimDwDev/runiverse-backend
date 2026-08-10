package com.runiverse.e2e;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 배포될 도커 이미지를 그대로 띄운 뒤 컨테이너 밖에서 HTTP로만 검증하는 E2E의 공통 뼈대.
 * 프로덕션 모듈과 분리된 프로젝트라 앱 클래스를 참조할 수 없고, 그래서 블랙박스가 강제된다.
 */
public abstract class E2eTestSupport {

    // run-e2e.sh가 export한 값을 그대로 받는다
    private static final String BASE_URL =
            System.getenv().getOrDefault("E2E_BASE_URL", "http://localhost:8080/api/v1");
    private static final String APP_CONTAINER =
            System.getenv().getOrDefault("E2E_APP_CONTAINER", "runiverse-e2e-app");
    private static final String MAIL_LOG_MARKER = "[메일 발송 생략]";
    private static final Pattern VERIFICATION_CODE = Pattern.compile("\\d{6}");
    private static final int CODE_LOOKUP_RETRIES = 10;
    private static final int MAIL_BODY_LINES = 5;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    public record Response(int status, Map<String, Object> body) {

        public String text(String field) {
            return (String) body.get(field);
        }
    }

    protected Response post(String path, Map<String, ?> request) {
        return post(path, request, null);
    }

    protected Response post(String path, Map<String, ?> request, String accessToken) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + path))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(toJson(request), StandardCharsets.UTF_8));
        if (accessToken != null) {
            builder.header("Authorization", "Bearer " + accessToken);
        }
        HttpResponse<String> response = send(builder.build());
        return new Response(response.statusCode(), parse(response.body()));
    }

    // DB를 비울 수 없으므로 테스트마다 겹치지 않는 값을 쓴다
    protected String uniqueEmail() {
        return "runner-" + shortId() + "@runiverse.test";
    }

    // 닉네임은 2~16자에 한글·영문·숫자·_ 만 허용된다
    protected String uniqueNickname() {
        return "runner" + shortId();
    }

    /**
     * 인증 코드는 Redis에 해시로만 남아 되돌릴 수 없다.
     * local 프로필의 LoggingEmailAdapter가 본문째로 찍은 로그에서 회수한다.
     */
    protected String sentVerificationCode(String email) {
        for (int attempt = 0; attempt < CODE_LOOKUP_RETRIES; attempt++) {
            String code = findCode(containerLogs(), email);
            if (code != null) {
                return code;
            }
            sleepBriefly();
        }
        throw new IllegalStateException("앱 로그에서 %s 의 인증 코드를 찾지 못했습니다".formatted(email));
    }

    private static String findCode(String logs, String email) {
        String[] lines = logs.split("\\R");
        // 같은 이메일로 여러 번 보냈다면 가장 마지막 발송을 쓴다
        for (int i = lines.length - 1; i >= 0; i--) {
            if (!lines[i].contains(MAIL_LOG_MARKER) || !lines[i].contains("to=" + email)) {
                continue;
            }
            // 코드는 마커 다음 줄들의 본문에 있다
            for (int j = i + 1; j < Math.min(i + MAIL_BODY_LINES, lines.length); j++) {
                Matcher matcher = VERIFICATION_CODE.matcher(lines[j]);
                if (matcher.find()) {
                    return matcher.group();
                }
            }
        }
        return null;
    }

    private static String containerLogs() {
        try {
            Process process = new ProcessBuilder("docker", "logs", APP_CONTAINER)
                    .redirectErrorStream(true)
                    .start();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            process.waitFor();
            return output;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("앱 로그를 읽지 못했습니다", e);
        }
    }

    private static HttpResponse<String> send(HttpRequest request) {
        try {
            return HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("요청이 중단되었습니다", e);
        }
    }

    private static String toJson(Object value) {
        try {
            return OBJECT_MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("요청 직렬화에 실패했습니다", e);
        }
    }

    private static Map<String, Object> parse(String body) {
        // 204처럼 본문이 없는 응답도 있다
        if (body == null || body.isBlank()) {
            return Map.of();
        }
        try {
            return OBJECT_MAPPER.readValue(body, new TypeReference<Map<String, Object>>() {
            });
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("응답이 JSON이 아닙니다: " + body, e);
        }
    }

    private static String shortId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    private static void sleepBriefly() {
        try {
            Thread.sleep(200);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
