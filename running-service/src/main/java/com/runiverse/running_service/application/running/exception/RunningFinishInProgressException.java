package com.runiverse.running_service.application.running.exception;

import com.runiverse.running_service.application.common.exception.BusinessException;
import com.runiverse.running_service.application.common.exception.RunningErrorCode;

// 다른 연결이 같은 러닝의 끝내기를 처리하는 중이다(끝내기 임대가 있다).
// 코드는 RUNNING_TRACK_UNAVAILABLE을 그대로 쓴다 — 앱이 이미 '확정되지 않았으니 잠시 뒤 RUNNING_FINISH를 다시 보낸다'로
// 처리해 앱 수정이 없다(D-82). 저장소 장애와 가르는 것은 이 예외 이름과 runiverse.running.finish.inprogress다
public class RunningFinishInProgressException extends BusinessException {

    public RunningFinishInProgressException() {
        super(RunningErrorCode.RUNNING_TRACK_UNAVAILABLE);
    }
}
