package com.runiverse.running_service.unit_test.user.application;

import ch.qos.logback.classic.Level;
import com.github.f4b6a3.uuid.UuidCreator;
import com.runiverse.running_service.application.user.command.stream.CloseUserStreamCommand;
import com.runiverse.running_service.application.user.command.stream.CloseUserStreamHandler;
import com.runiverse.running_service.application.user.port.out.UserStreamConnection;
import com.runiverse.running_service.application.user.port.out.UserStreamPort;
import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.support.LogCapture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("유저 스트림 종료 단위 테스트")
class CloseUserStreamHandlerTest {

    private static final UUID USER_ID = UuidCreator.getTimeOrderedEpoch();

    @Mock
    private UserStreamPort userStreamPort;

    @Mock
    private UserStreamConnection connection;

    private CloseUserStreamHandler handler;
    private LogCapture log;

    @BeforeEach
    void setUp() {
        handler = new CloseUserStreamHandler(userStreamPort);
        log = LogCapture.of(CloseUserStreamHandler.class);
    }

    @AfterEach
    void tearDown() {
        log.stop();
    }

    @Test
    @DisplayName("끊긴 그 연결을 레지스트리에서 뺀다")
    void removesClosedConnection() {
        // given
        given(connection.id()).willReturn("conn-1");
        given(userStreamPort.remove(new UserId(USER_ID), connection)).willReturn(true);

        // when
        handler.handle(new CloseUserStreamCommand(USER_ID, connection));

        // then -> 사람이 아니라 연결 기준으로 지운다. 같은 사람의 새 연결은 건드리지 않는다
        verify(userStreamPort).remove(new UserId(USER_ID), connection);
    }

    @Test
    @DisplayName("스트림 종료는 레지스트리 제거 여부를 영문 key로 담아 남긴다")
    void logsCloseWithParsableKeys() {
        // given -> 새 연결이 이미 자리를 가져간 경우라 지우지 않는다
        given(connection.id()).willReturn("conn-1");
        given(userStreamPort.remove(new UserId(USER_ID), connection)).willReturn(false);

        // when
        handler.handle(new CloseUserStreamCommand(USER_ID, connection));

        // then
        assertThat(log.messages(Level.INFO)).containsExactly(
                "[회원] 스트림 종료 성공 - userId=" + USER_ID + ", connectionId=conn-1, removed=false");
    }
}
