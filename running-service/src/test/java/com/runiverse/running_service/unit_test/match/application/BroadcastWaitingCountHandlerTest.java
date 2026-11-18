package com.runiverse.running_service.unit_test.match.application;

import com.runiverse.running_service.application.app.port.out.AppStreamConnection;
import com.runiverse.running_service.application.app.port.out.AppStreamEvent;
import com.runiverse.running_service.application.app.port.out.AppStreamEventType;
import com.runiverse.running_service.application.app.port.out.AppStreamPort;
import com.runiverse.running_service.application.match.command.waiting.BroadcastWaitingCountHandler;
import com.runiverse.running_service.application.match.command.waiting.WaitingCountReader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
@DisplayName("홈 대기 인원 전송 단위 테스트")
class BroadcastWaitingCountHandlerTest {

    @Mock
    private AppStreamPort appStreamPort;

    @Mock
    private WaitingCountReader waitingCountReader;

    @Mock
    private AppStreamConnection first;

    @Mock
    private AppStreamConnection second;

    private BroadcastWaitingCountHandler handler;

    @BeforeEach
    void setUp() {
        handler = new BroadcastWaitingCountHandler(appStreamPort, waitingCountReader);
    }

    @Test
    @DisplayName("이 인스턴스에 붙은 연결이 없으면 계산하지 않는다")
    void skipsReadingWithoutConnections() {
        // given -> 다른 인스턴스에만 사람이 붙어 있다
        given(appStreamPort.findAll()).willReturn(List.of());

        // when
        handler.broadcast();

        // then
        verifyNoInteractions(waitingCountReader);
    }

    @Test
    @DisplayName("한 번 계산한 합계를 붙은 연결 전부에 HOME_WAITING으로 보낸다")
    void sendsSameCountToEveryConnection() {
        // given
        given(appStreamPort.findAll()).willReturn(List.of(first, second));
        given(waitingCountReader.read()).willReturn(4);

        // when
        handler.broadcast();

        // then
        AppStreamEvent expected = AppStreamEvent.homeWaiting(4);
        assertThat(expected.type()).isEqualTo(AppStreamEventType.HOME_WAITING);
        verify(first).send(expected);
        verify(second).send(expected);
    }

    @Test
    @DisplayName("한 연결이 실패해도 나머지에게는 보낸다")
    void oneFailureDoesNotBlockOthers() {
        // given -> 연결 순서가 고정이라 앞 연결의 실패가 막으면 같은 사람들이 주기마다 빠진다
        AppStreamEvent event = AppStreamEvent.homeWaiting(4);
        given(appStreamPort.findAll()).willReturn(List.of(first, second));
        given(waitingCountReader.read()).willReturn(4);
        doThrow(new IllegalStateException("직렬화 실패")).when(first).send(event);
        given(first.id()).willReturn("conn-1");

        // when
        handler.broadcast();

        // then
        verify(second).send(event);
    }

    @Test
    @DisplayName("핸들러는 트랜잭션을 열지 않는다")
    void opensNoTransaction() throws NoSuchMethodException {
        // when & then -> 붙은 연결이 없는 인스턴스가 주기마다 DB 커넥션을 잡지 않는다. 읽기 트랜잭션은 리더가 연다
        assertThat(BroadcastWaitingCountHandler.class.getAnnotation(Transactional.class)).isNull();
        assertThat(BroadcastWaitingCountHandler.class.getMethod("broadcast")
                .getAnnotation(Transactional.class)).isNull();
    }
}
