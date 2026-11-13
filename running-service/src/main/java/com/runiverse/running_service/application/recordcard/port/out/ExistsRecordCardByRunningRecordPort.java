package com.runiverse.running_service.application.recordcard.port.out;

import com.runiverse.running_service.domain.common.vo.RunningRecordId;

public interface ExistsRecordCardByRunningRecordPort {

    // 이 기록에 카드가 있는지 — 기록과 카드는 1:1이라 있으면 작성 요청을 409로 거절한다.
    // 지운 카드는 행이 없다(하드 삭제) — 다시 만들면 새 카드다
    boolean existsByRunningRecord(RunningRecordId runningRecordId);
}
