package com.runiverse.running_service.application.recordcard.port.in;

import com.runiverse.running_service.application.recordcard.command.retrymusic.RetryRecordCardMusicCommand;
import com.runiverse.running_service.application.recordcard.query.detail.GetRecordCardDetailResult;

public interface RetryRecordCardMusicUsecase {

    // 실패한 음악이 있으면 카드의 음악 전체를 다시 요청한다. 응답은 반영 뒤 상세(8-1)다
    GetRecordCardDetailResult handle(RetryRecordCardMusicCommand command);
}
