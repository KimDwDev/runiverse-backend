package com.runiverse.running_service.unit_test.user.application;

import com.github.f4b6a3.uuid.UuidCreator;
import com.runiverse.running_service.application.user.command.stream.OpenUserStreamCommand;
import com.runiverse.running_service.application.user.command.stream.OpenUserStreamHandler;
import com.runiverse.running_service.application.user.port.out.UserStreamConnection;
import com.runiverse.running_service.application.user.port.out.UserStreamPort;
import com.runiverse.running_service.domain.common.vo.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
@DisplayName("유저 스트림 연결 단위 테스트")
class OpenUserStreamHandlerTest {

    private static final UUID USER_ID = UuidCreator.getTimeOrderedEpoch();

    @Mock
    private UserStreamPort userStreamPort;

    @Mock
    private UserStreamConnection newConnection;

    @Mock
    private UserStreamConnection supersededConnection;

    @InjectMocks
    private OpenUserStreamHandler openUserStreamHandler;

    @Test
    @DisplayName("활성 신청과 무관하게 연결을 등록한다")
    void registersConnection() {
        // given -> 매칭 스트림과 달리 로그인만 하면 열린다
        given(userStreamPort.register(new UserId(USER_ID), newConnection)).willReturn(Optional.empty());

        // when
        openUserStreamHandler.handle(new OpenUserStreamCommand(USER_ID, newConnection));

        // then
        verify(userStreamPort).register(new UserId(USER_ID), newConnection);
    }

    @Test
    @DisplayName("밀려난 이전 연결은 닫는다")
    void closesSupersededConnection() {
        // given -> 앱 재시작처럼 끊긴 줄 모르고 남아 있는 옛 연결이 있다
        given(userStreamPort.register(new UserId(USER_ID), newConnection))
                .willReturn(Optional.of(supersededConnection));

        // when
        openUserStreamHandler.handle(new OpenUserStreamCommand(USER_ID, newConnection));

        // then -> 마지막 연결만 남긴다
        verify(supersededConnection).closeSuperseded();
    }

    @Test
    @DisplayName("밀어낼 연결이 없으면 아무 연결도 닫지 않는다")
    void closesNothingWhenNoSupersededConnection() {
        // given
        given(userStreamPort.register(new UserId(USER_ID), newConnection)).willReturn(Optional.empty());

        // when
        openUserStreamHandler.handle(new OpenUserStreamCommand(USER_ID, newConnection));

        // then
        verifyNoInteractions(supersededConnection);
    }
}
