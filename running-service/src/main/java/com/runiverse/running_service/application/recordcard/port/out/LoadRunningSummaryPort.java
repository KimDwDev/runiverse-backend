package com.runiverse.running_service.application.recordcard.port.out;

import com.runiverse.running_service.application.recordcard.port.out.RecordCardMusicRequest.RunningSummary;
import com.runiverse.running_service.domain.common.vo.RunningRecordId;

import java.util.Optional;

// 음악 생성 요청에 실을 러닝 기록의 수치 — 작성 확정(9-2)이 기록 행을 잠근 같은 트랜잭션에서 읽는다.
// 잠근 기록이라 보통은 있다 — 없으면 비어 있다
public interface LoadRunningSummaryPort {

    Optional<RunningSummary> load(RunningRecordId runningRecordId);
}
