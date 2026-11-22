package com.runiverse.running_service.application.recordcard.command.retrymusic;

import com.runiverse.running_service.application.recordcard.port.in.GetRecordCardDetailUsecase;
import com.runiverse.running_service.application.recordcard.port.in.RetryRecordCardMusicUsecase;
import com.runiverse.running_service.application.recordcard.query.detail.GetRecordCardDetailQuery;
import com.runiverse.running_service.application.recordcard.query.detail.GetRecordCardDetailResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

// 9-6 음악 다시 만들기 — 트랜잭션을 걸지 않는다. 반영(RecordCardMusicRetrier 트랜잭션)이 커밋된 뒤
// 상세(8-1)를 읽어 준다 — 9-3과 같은 구조다. 다시 만들었으면 음악이 전부 GENERATING인 모습이다
@Service
@RequiredArgsConstructor
public class RetryRecordCardMusicHandler implements RetryRecordCardMusicUsecase {

    private final RecordCardMusicRetrier recordCardMusicRetrier;
    private final GetRecordCardDetailUsecase getRecordCardDetailUsecase;

    @Override
    public GetRecordCardDetailResult handle(RetryRecordCardMusicCommand command) {
        recordCardMusicRetrier.retry(command.userId(), command.recordCardId());
        return getRecordCardDetailUsecase.handle(
                new GetRecordCardDetailQuery(command.userId(), command.recordCardId()));
    }
}
