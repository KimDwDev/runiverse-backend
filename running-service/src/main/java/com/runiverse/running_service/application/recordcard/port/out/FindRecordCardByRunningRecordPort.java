package com.runiverse.running_service.application.recordcard.port.out;

import com.runiverse.running_service.domain.common.vo.RunningRecordId;
import com.runiverse.running_service.domain.recordcard.vo.RecordCardId;

import java.util.Optional;

public interface FindRecordCardByRunningRecordPort {

    // 이 기록에 붙은 카드 — 기록과 카드는 1:1이고 지운 카드도 행이 남는다.
    // 살아 있으면 작성 요청을 409로 거절하고, 지웠으면 새 ID를 만들지 않고 그 카드의 ID로 되살린다
    Optional<ExistingRecordCard> findByRunningRecord(RunningRecordId runningRecordId);

    record ExistingRecordCard(RecordCardId recordCardId, boolean deleted) {

    }
}
