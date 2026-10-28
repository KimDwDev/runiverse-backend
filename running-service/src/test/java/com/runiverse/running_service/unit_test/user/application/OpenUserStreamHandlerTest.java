package com.runiverse.running_service.unit_test.user.application;

import com.github.f4b6a3.uuid.UuidCreator;
import com.runiverse.running_service.application.running.port.in.GetUserStatusUsecase;
import com.runiverse.running_service.application.running.query.status.GetUserStatusQuery;
import com.runiverse.running_service.application.running.query.status.GetUserStatusResult;
import com.runiverse.running_service.application.running.query.status.UserRunningStatus;
import com.runiverse.running_service.application.user.command.stream.OpenUserStreamCommand;
import com.runiverse.running_service.application.user.command.stream.OpenUserStreamHandler;
import com.runiverse.running_service.application.user.port.out.UserStreamConnection;
import com.runiverse.running_service.application.user.port.out.UserStreamEvent;
import com.runiverse.running_service.application.user.port.out.UserStreamEventType;
import com.runiverse.running_service.application.user.port.out.UserStreamPort;
import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.domain.running.room.vo.RunningRoomType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
@DisplayName("유저 스트림 연결 단위 테스트")
class OpenUserStreamHandlerTest {

    private static final UUID USER_ID = UuidCreator.getTimeOrderedEpoch();
    private static final GetUserStatusResult WAITING = new GetUserStatusResult(
            UserRunningStatus.WAITING, RunningRoomType.MATCH, 125L,
            LocalDateTime.now().plusHours(1), 5_000, null);

    @Mock
    private UserStreamPort userStreamPort;

    @Mock
    private GetUserStatusUsecase getUserStatusUsecase;

    @Mock
    private UserStreamConnection newConnection;

    @Mock
    private UserStreamConnection supersededConnection;

    @InjectMocks
    private OpenUserStreamHandler openUserStreamHandler;

    @Test
    @DisplayName("등록한 뒤 2-2와 같은 상태를 USER_STATUS 스냅샷으로 보낸다")
    void registersThenSendsStatusSnapshot() {
        // given
        given(userStreamPort.register(new UserId(USER_ID), newConnection)).willReturn(Optional.empty());
        given(getUserStatusUsecase.handle(new GetUserStatusQuery(USER_ID))).willReturn(WAITING);

        // when
        openUserStreamHandler.handle(new OpenUserStreamCommand(USER_ID, newConnection));

        // then -> 등록이 먼저다. 스냅샷을 계산하는 사이에 생긴 이벤트도 이 연결로 받아야 한다
        InOrder order = inOrder(userStreamPort, newConnection);
        order.verify(userStreamPort).register(new UserId(USER_ID), newConnection);
        ArgumentCaptor<UserStreamEvent> sent = ArgumentCaptor.forClass(UserStreamEvent.class);
        order.verify(newConnection).send(sent.capture());
        assertThat(sent.getValue().type()).isEqualTo(UserStreamEventType.USER_STATUS);
        assertThat(sent.getValue().status()).isEqualTo(WAITING);
    }

    @Test
    @DisplayName("활성 신청이 없어도 연결을 열고 IDLE 스냅샷을 보낸다")
    void opensForIdleUser() {
        // given -> 매칭 스트림과 달리 로그인만 하면 열린다
        GetUserStatusResult idle = GetUserStatusResult.idle(null);
        given(userStreamPort.register(new UserId(USER_ID), newConnection)).willReturn(Optional.empty());
        given(getUserStatusUsecase.handle(new GetUserStatusQuery(USER_ID))).willReturn(idle);

        // when
        openUserStreamHandler.handle(new OpenUserStreamCommand(USER_ID, newConnection));

        // then
        verify(newConnection).send(UserStreamEvent.status(idle));
    }

    @Test
    @DisplayName("밀려난 이전 연결은 닫는다")
    void closesSupersededConnection() {
        // given -> 앱 재시작처럼 끊긴 줄 모르고 남아 있는 옛 연결이 있다
        given(userStreamPort.register(new UserId(USER_ID), newConnection))
                .willReturn(Optional.of(supersededConnection));
        given(getUserStatusUsecase.handle(new GetUserStatusQuery(USER_ID))).willReturn(WAITING);

        // when
        openUserStreamHandler.handle(new OpenUserStreamCommand(USER_ID, newConnection));

        // then -> 마지막 연결만 남긴다. 스냅샷은 새 연결로만 간다
        verify(supersededConnection).closeSuperseded();
        verify(newConnection).send(UserStreamEvent.status(WAITING));
    }

    @Test
    @DisplayName("밀어낼 연결이 없으면 아무 연결도 닫지 않는다")
    void closesNothingWhenNoSupersededConnection() {
        // given
        given(userStreamPort.register(new UserId(USER_ID), newConnection)).willReturn(Optional.empty());
        given(getUserStatusUsecase.handle(new GetUserStatusQuery(USER_ID))).willReturn(WAITING);

        // when
        openUserStreamHandler.handle(new OpenUserStreamCommand(USER_ID, newConnection));

        // then
        verifyNoInteractions(supersededConnection);
    }
}
