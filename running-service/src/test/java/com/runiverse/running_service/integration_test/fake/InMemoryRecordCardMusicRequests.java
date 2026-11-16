package com.runiverse.running_service.integration_test.fake;

import com.runiverse.running_service.application.recordcard.port.out.RecordCardMusicRequest;
import com.runiverse.running_service.application.recordcard.port.out.RequestRecordCardMusicPort;

import java.util.ArrayList;
import java.util.List;

// 아웃박스 대신 받은 음악 요청을 모아 둔다 — 트랜잭션 롤백은 흉내 내지 않는다(저장이 실패하는 경로는 요청 전에 예외로 끝난다).
// 아웃박스 행·본문 모양은 어댑터 단위 테스트와 E2E가 본다
public class InMemoryRecordCardMusicRequests implements RequestRecordCardMusicPort {

    private final List<RecordCardMusicRequest> requests = new ArrayList<>();

    @Override
    public void request(RecordCardMusicRequest request) {
        requests.add(request);
    }

    public List<RecordCardMusicRequest> requests() {
        return List.copyOf(requests);
    }
}
