package com.runiverse.running_service.application.recordcard.port.out;

import com.runiverse.running_service.domain.common.vo.RunningRecordId;

public interface ExistsLiveRecordCardPort {

    // 이 기록에 지우지 않은 카드가 있는지 — 있으면 작성 요청을 409로 거절한다.
    // 지운 카드는 세지 않는다 — 다시 만들면 그 행을 되살린다(9-2)
    boolean existsLiveByRunningRecord(RunningRecordId runningRecordId);
}
