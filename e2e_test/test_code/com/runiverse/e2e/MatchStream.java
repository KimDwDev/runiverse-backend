package com.runiverse.e2e;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Stream;

/**
 * 매칭 이벤트 스트림(SSE)을 컨테이너 밖에서 구독하는 테스트 클라이언트.
 * SSE는 줄 단위 텍스트라 라이브러리 없이 JDK HttpClient의 줄 스트림만으로 읽는다.
 */
public final class MatchStream implements AutoCloseable {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(10);
    private static final String EVENT_PREFIX = "event:";
    private static final String DATA_PREFIX = "data:";
    // 서버 이벤트 이름과 겹치지 않는 내부 표지
    private static final String STREAM_ENDED = "__STREAM_ENDED__";

    private final int status;
    private final Stream<String> lines;
    private final BlockingQueue<Map<String, Object>> received;

    private MatchStream(int status, Stream<String> lines, BlockingQueue<Map<String, Object>> received) {
        this.status = status;
        this.lines = lines;
        this.received = received;
    }

    static MatchStream open(HttpClient httpClient, String url, String accessToken) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Accept", "text/event-stream")
                .header("Authorization", "Bearer " + accessToken)
                .GET()
                .build();
        // 요청 timeout은 걸지 않는다 — 응답 헤더 뒤에도 본문이 계속 흘러오는 연결이다.
        // 헤더가 오기까지만 여기서 기다린다
        HttpResponse<Stream<String>> response = awaitHeaders(
                httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofLines()));
        BlockingQueue<Map<String, Object>> received = new LinkedBlockingQueue<>();
        Stream<String> lines = response.body();
        if (response.statusCode() == 200) {
            Thread reader = new Thread(() -> collect(lines, received), "match-stream-reader");
            // 닫지 못한 스트림이 테스트 JVM 종료를 붙잡지 않게 한다
            reader.setDaemon(true);
            reader.start();
        }
        return new MatchStream(response.statusCode(), lines, received);
    }

    public int status() {
        return status;
    }

    /** 기다리는 이벤트가 올 때까지 나머지는 흘려보내고, 온 이벤트의 data(JSON)를 꺼낸다. */
    public Map<String, Object> await(String event) {
        long deadline = System.nanoTime() + DEFAULT_TIMEOUT.toNanos();
        while (true) {
            long remaining = deadline - System.nanoTime();
            if (remaining <= 0) {
                throw new IllegalStateException("%s 를 %s 안에 받지 못했습니다".formatted(event, DEFAULT_TIMEOUT));
            }
            Map<String, Object> message = poll(remaining);
            if (message == null) {
                continue;
            }
            if (STREAM_ENDED.equals(message.get("event"))) {
                // 끝난 스트림에는 더 올 것이 없다 — 다음 대기도 바로 끝나게 표지를 되돌려 둔다
                received.add(message);
                throw new IllegalStateException(
                        "%s 를 기다리는 중 스트림이 끝났습니다 — %s".formatted(event, message.get("data")));
            }
            if (event.equals(message.get("event"))) {
                return parse((String) message.get("data"));
            }
        }
    }

    @Override
    public void close() {
        // 본문 스트림을 닫으면 구독이 취소되고 연결이 끊긴다 — 서버는 이걸 클라 종료로 받는다
        lines.close();
    }

    // 빈 줄이 이벤트 하나의 끝이다. ':'로 시작하는 줄(keep-alive 주석)은 흘린다
    private static void collect(Stream<String> lines, BlockingQueue<Map<String, Object>> received) {
        StringBuilder data = new StringBuilder();
        String[] event = {null};
        try {
            lines.forEach(line -> {
                if (line.isEmpty()) {
                    if (event[0] != null) {
                        received.add(new HashMap<>(Map.of("event", event[0], "data", data.toString())));
                    }
                    event[0] = null;
                    data.setLength(0);
                } else if (line.startsWith(EVENT_PREFIX)) {
                    event[0] = line.substring(EVENT_PREFIX.length()).trim();
                } else if (line.startsWith(DATA_PREFIX)) {
                    data.append(line.substring(DATA_PREFIX.length()).trim());
                }
            });
            // 서버가 complete()로 닫은 경우다(연결 교체·회원탈퇴)
            received.add(Map.of("event", STREAM_ENDED, "data", "서버가 스트림을 닫았습니다"));
        } catch (RuntimeException e) {
            // close()로 끊어도 여기로 온다 — 그때는 기다리는 쪽이 없어 표지가 그냥 남는다.
            // 대기 중에 끊긴 것이면 원인이 타임아웃으로 뭉개지지 않게 대기 쪽에서 드러낸다
            received.add(Map.of("event", STREAM_ENDED, "data", e.toString()));
        }
    }

    private Map<String, Object> poll(long remainingNanos) {
        try {
            return received.poll(remainingNanos, TimeUnit.NANOSECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("이벤트 대기가 중단되었습니다", e);
        }
    }

    private static HttpResponse<Stream<String>> awaitHeaders(
            CompletableFuture<HttpResponse<Stream<String>>> future) {
        try {
            return future.get(DEFAULT_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            abandon(future);
            throw new IllegalStateException("스트림 연결이 중단되었습니다", e);
        } catch (TimeoutException e) {
            abandon(future);
            throw new IllegalStateException("스트림 연결에 실패했습니다", e);
        } catch (ExecutionException e) {
            throw new IllegalStateException("스트림 연결에 실패했습니다", e);
        }
    }

    // 포기한 요청이 뒤늦게 응답하면 읽을 주체가 없는 연결이 남는다 — 취소하고,
    // 취소보다 응답이 먼저 와 있었으면 그 본문을 닫는다
    private static void abandon(CompletableFuture<HttpResponse<Stream<String>>> future) {
        if (!future.cancel(true)) {
            HttpResponse<Stream<String>> response = future.getNow(null);
            if (response != null) {
                response.body().close();
            }
        }
    }

    private static Map<String, Object> parse(String payload) {
        try {
            return OBJECT_MAPPER.readValue(payload, new TypeReference<Map<String, Object>>() {
            });
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("이벤트 data가 JSON이 아닙니다: " + payload, e);
        }
    }
}
