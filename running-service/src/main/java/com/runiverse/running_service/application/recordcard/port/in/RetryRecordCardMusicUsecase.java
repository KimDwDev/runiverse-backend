package com.runiverse.running_service.application.recordcard.port.in;

import com.runiverse.running_service.application.recordcard.command.retrymusic.RetryRecordCardMusicCommand;

public interface RetryRecordCardMusicUsecase {

    // 실패한 음악이 있으면 카드의 음악 전체를 다시 요청한다. 없으면 아무것도 하지 않는다 — 카드는 8-1로 다시 읽는다
    void handle(RetryRecordCardMusicCommand command);
}
