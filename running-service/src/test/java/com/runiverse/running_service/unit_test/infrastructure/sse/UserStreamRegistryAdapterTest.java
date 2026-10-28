package com.runiverse.running_service.unit_test.infrastructure.sse;

import com.github.f4b6a3.uuid.UuidCreator;
import com.runiverse.running_service.application.user.port.out.UserStreamConnection;
import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.infrastructure.sse.UserStreamRegistryAdapter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

@DisplayName("유저 스트림 레지스트리 단위 테스트")
class UserStreamRegistryAdapterTest {

    private static final UserId USER_ID = new UserId(UuidCreator.getTimeOrderedEpoch());
    private static final UserId OTHER_USER_ID = new UserId(UuidCreator.getTimeOrderedEpoch());

    private UserStreamRegistryAdapter userStreamRegistryAdapter;

    @BeforeEach
    void setUp() {
        userStreamRegistryAdapter = new UserStreamRegistryAdapter();
    }

    @Test
    @DisplayName("같은 유저가 다시 연결하면 밀려난 이전 연결을 돌려준다")
    void returnsSupersededConnectionOnReconnect() {
        // given -> 앱 재시작처럼 옛 연결이 끊긴 줄 모르고 남아 있는 상황
        UserStreamConnection first = connection("conn-1");
        UserStreamConnection second = connection("conn-2");
        userStreamRegistryAdapter.register(USER_ID, first);

        // when
        Optional<UserStreamConnection> superseded = userStreamRegistryAdapter.register(USER_ID, second);

        // then
        assertThat(superseded).containsSame(first);
        assertThat(userStreamRegistryAdapter.find(USER_ID)).containsSame(second);
    }

    @Test
    @DisplayName("같은 연결이 다시 등록되면 밀어낼 것이 없다")
    void returnsEmptyWhenSameConnectionRegistersAgain() {
        // given
        UserStreamConnection connection = connection("conn-1");
        userStreamRegistryAdapter.register(USER_ID, connection);

        // when
        Optional<UserStreamConnection> superseded = userStreamRegistryAdapter.register(USER_ID, connection);

        // then
        assertThat(superseded).isEmpty();
    }

    @Test
    @DisplayName("다른 유저의 연결은 서로 밀어내지 않는다")
    void keepsConnectionsOfDifferentUsers() {
        // given
        UserStreamConnection mine = connection("conn-1");
        UserStreamConnection others = connection("conn-2");
        userStreamRegistryAdapter.register(USER_ID, mine);

        // when
        Optional<UserStreamConnection> superseded = userStreamRegistryAdapter.register(OTHER_USER_ID, others);

        // then
        assertThat(superseded).isEmpty();
        assertThat(userStreamRegistryAdapter.find(USER_ID)).containsSame(mine);
        assertThat(userStreamRegistryAdapter.find(OTHER_USER_ID)).containsSame(others);
    }

    @Test
    @DisplayName("새 연결이 자리를 가져간 뒤에는 옛 연결이 그 자리를 지우지 못한다")
    void keepsNewConnectionWhenSupersededOneIsRemovedLate() {
        // given -> 재연결 직후 옛 연결의 종료 콜백이 뒤늦게 도는 상황
        UserStreamConnection first = connection("conn-1");
        UserStreamConnection second = connection("conn-2");
        userStreamRegistryAdapter.register(USER_ID, first);
        userStreamRegistryAdapter.register(USER_ID, second);

        // when
        boolean removed = userStreamRegistryAdapter.remove(USER_ID, first);

        // then
        assertThat(removed).isFalse();
        assertThat(userStreamRegistryAdapter.find(USER_ID)).containsSame(second);
    }

    @Test
    @DisplayName("등록된 그 연결을 넘기면 지워진다")
    void removesRegisteredConnection() {
        // given
        UserStreamConnection connection = connection("conn-1");
        userStreamRegistryAdapter.register(USER_ID, connection);

        // when
        boolean removed = userStreamRegistryAdapter.remove(USER_ID, connection);

        // then
        assertThat(removed).isTrue();
        assertThat(userStreamRegistryAdapter.find(USER_ID)).isEmpty();
    }

    // 이 테스트는 연결 수명만 다룬다 — 식별자 말고는 연결의 동작을 쓰지 않는다
    private static UserStreamConnection connection(String id) {
        UserStreamConnection connection = mock(UserStreamConnection.class);
        lenient().when(connection.id()).thenReturn(id);
        return connection;
    }
}
