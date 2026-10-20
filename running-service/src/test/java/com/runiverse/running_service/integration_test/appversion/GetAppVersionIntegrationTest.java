package com.runiverse.running_service.integration_test.appversion;

import com.runiverse.running_service.application.appversion.query.version.GetAppVersionHandler;
import com.runiverse.running_service.application.appversion.query.version.GetAppVersionQuery;
import com.runiverse.running_service.application.appversion.query.version.GetAppVersionResult;
import com.runiverse.running_service.domain.appversion.vo.Platform;
import com.runiverse.running_service.integration_test.fake.InMemoryAppVersionPolicyStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("앱 버전 확인 통합 테스트")
public class GetAppVersionIntegrationTest {

    private InMemoryAppVersionPolicyStore appVersionPolicyStore;
    private GetAppVersionHandler handler;

    @BeforeEach
    void setUp() {
        appVersionPolicyStore = new InMemoryAppVersionPolicyStore();
        handler = new GetAppVersionHandler(
                appVersionPolicyStore   // LoadAppVersionPolicyPort
        );
    }

    @Test
    @DisplayName("플랫폼마다 따로 둔 값을 내린다")
    void returnsValuesPerPlatform() {
        // given -> 심사·공개 시점이 달라 한쪽에만 나온 버전을 다른 쪽에 요구하지 않는다
        appVersionPolicyStore.register(Platform.IOS, "1.0.0", "1.1.0");
        appVersionPolicyStore.register(Platform.ANDROID, "1.1.0", "1.2.0");

        // when
        GetAppVersionResult ios = handler.handle(new GetAppVersionQuery("IOS"));
        GetAppVersionResult android = handler.handle(new GetAppVersionQuery("android"));

        // then
        assertThat(ios).isEqualTo(new GetAppVersionResult("1.0.0", "1.1.0"));
        assertThat(android).isEqualTo(new GetAppVersionResult("1.1.0", "1.2.0"));
    }

    @Test
    @DisplayName("운영이 값을 바꾸면 다음 조회부터 바로 반영된다")
    void changedValueIsReadOnNextQuery() {
        // given
        appVersionPolicyStore.register(Platform.ANDROID, "1.0.0", "1.1.0");
        handler.handle(new GetAppVersionQuery("ANDROID"));

        // when -> 캐시를 두지 않는다
        appVersionPolicyStore.register(Platform.ANDROID, "1.1.0", "1.2.0");
        GetAppVersionResult result = handler.handle(new GetAppVersionQuery("ANDROID"));

        // then
        assertThat(result).isEqualTo(new GetAppVersionResult("1.1.0", "1.2.0"));
    }

    @Test
    @DisplayName("플랫폼 행이 없으면 값을 지어내지 않고 실패한다")
    void missingRowFails() {
        // given
        appVersionPolicyStore.register(Platform.IOS, "1.0.0", "1.0.0");

        // when & then
        assertThatThrownBy(() -> handler.handle(new GetAppVersionQuery("ANDROID")))
                .isInstanceOf(IllegalStateException.class);
    }
}
