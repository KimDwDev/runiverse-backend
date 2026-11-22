package com.runiverse.running_service.unit_test.recordcard.application;

import com.github.f4b6a3.uuid.UuidCreator;
import com.runiverse.running_service.application.recordcard.command.retrymusic.RecordCardMusicRetrier;
import com.runiverse.running_service.application.recordcard.command.retrymusic.RetryRecordCardMusicCommand;
import com.runiverse.running_service.application.recordcard.command.retrymusic.RetryRecordCardMusicHandler;
import com.runiverse.running_service.application.recordcard.exception.RecordCardNotFoundException;
import com.runiverse.running_service.application.recordcard.port.in.GetRecordCardDetailUsecase;
import com.runiverse.running_service.application.recordcard.query.detail.GetRecordCardDetailQuery;
import com.runiverse.running_service.application.recordcard.query.detail.GetRecordCardDetailResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("기록 카드 음악 다시 만들기(9-6) 핸들러 단위 테스트")
class RetryRecordCardMusicHandlerTest {

    private static final UUID USER_ID = UuidCreator.getTimeOrderedEpoch();
    private static final UUID CARD_ID = UuidCreator.getTimeOrderedEpoch();

    @Mock
    private RecordCardMusicRetrier recordCardMusicRetrier;
    @Mock
    private GetRecordCardDetailUsecase getRecordCardDetailUsecase;

    private RetryRecordCardMusicHandler handler;

    @BeforeEach
    void setUp() {
        handler = new RetryRecordCardMusicHandler(recordCardMusicRetrier, getRecordCardDetailUsecase);
    }

    @Test
    @DisplayName("반영한 뒤 상세(8-1)를 읽어 준다 — 반영 트랜잭션이 끝난 뒤의 모습이다")
    void retriesThenReadsDetail() {
        // given
        GetRecordCardDetailResult detail = mock(GetRecordCardDetailResult.class);
        when(getRecordCardDetailUsecase.handle(new GetRecordCardDetailQuery(USER_ID, CARD_ID))).thenReturn(detail);

        // when
        GetRecordCardDetailResult result = handler.handle(new RetryRecordCardMusicCommand(USER_ID, CARD_ID));

        // then
        assertThat(result).isSameAs(detail);
        InOrder order = inOrder(recordCardMusicRetrier, getRecordCardDetailUsecase);
        order.verify(recordCardMusicRetrier).retry(USER_ID, CARD_ID);
        order.verify(getRecordCardDetailUsecase).handle(new GetRecordCardDetailQuery(USER_ID, CARD_ID));
    }

    @Test
    @DisplayName("반영이 404면 상세를 읽지 않는다")
    void notFound() {
        // given
        doThrow(new RecordCardNotFoundException()).when(recordCardMusicRetrier).retry(USER_ID, CARD_ID);

        // when & then
        assertThatThrownBy(() -> handler.handle(new RetryRecordCardMusicCommand(USER_ID, CARD_ID)))
                .isInstanceOf(RecordCardNotFoundException.class);
        verifyNoInteractions(getRecordCardDetailUsecase);
    }
}
