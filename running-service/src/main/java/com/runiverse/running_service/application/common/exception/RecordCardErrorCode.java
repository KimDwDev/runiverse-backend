package com.runiverse.running_service.application.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

// 기록 카드 — 기록이 없거나 남의 기록이면 ResourceErrorCode.NOT_FOUND를 쓴다.
// 남의 기록이 있는지 없는지를 알려 주지 않으려고 404는 가르지 않는다
@Getter
@RequiredArgsConstructor
public enum RecordCardErrorCode implements ErrorCode {
    // 이 기록에 지우지 않은 카드가 이미 있다 — 기록과 카드는 1:1이다. 지운 카드는 되살리므로 여기 걸리지 않는다
    RECORD_CARD_ALREADY_EXISTS("RECORD_CARD_ALREADY_EXISTS", "이미 기록 카드가 있는 러닝입니다."),
    // 사진이 최대 크기(record-card.draft.max-image-size)를 넘는다 — 운영값이라 Request DTO가 못 보고 유스케이스가 본다.
    // 클라에는 요청 검증 실패와 같은 코드로 보인다. 실제 문구는 예외가 운영값으로 만든다
    RECORD_CARD_IMAGE_TOO_LARGE("INVALID_REQUEST", "이미지가 업로드할 수 있는 크기를 넘었습니다.");
    private final String code;
    private final String message;
}
