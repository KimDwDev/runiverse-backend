package com.runiverse.running_service.application.recordcard.port.out;

import com.runiverse.running_service.domain.recordcard.RecordCard;

// 바뀌기 직전 카드(사진 포함)를 이력으로 남긴다 — 신고·분쟁 대응용이라 API로 내려주지 않는다.
// 카드 쓰기와 같은 트랜잭션에서, 기록 행을 잠그고 다시 읽은 카드로 부른다(잠금 안이라 직전 모습과 순서가 어긋나지 않는다).
// 바꾸기 전에 불러야 한다 — 인자로 받은 카드가 곧 남길 모습이다
public interface SnapshotRecordCardPort {

    void snapshot(RecordCard recordCard, RecordCardSnapshotReason reason);
}
