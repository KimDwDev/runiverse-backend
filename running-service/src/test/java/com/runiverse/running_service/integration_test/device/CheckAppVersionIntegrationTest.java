package com.runiverse.running_service.integration_test.device;

import com.runiverse.running_service.application.device.query.version.CheckAppVersionHandler;
import com.runiverse.running_service.application.device.query.version.CheckAppVersionQuery;
import com.runiverse.running_service.domain.device.vo.Platform;
import com.runiverse.running_service.integration_test.fake.InMemoryAppVersionPolicyStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("앱 버전 판정 통합 테스트")
public class CheckAppVersionIntegrationTest {

    private InMemoryAppVersionPolicyStore appVersionPolicyStore;
    private CheckAppVersionHandler handler;

    @BeforeEach
    void setUp() {
        appVersionPolicyStore = new InMemoryAppVersionPolicyStore();
        handler = new CheckAppVersionHandler(
                appVersionPolicyStore   // LoadAppVersionPolicyPort
        );
    }

    @Test
    @DisplayName("운영이 최소 버전을 올리면 다음 요청부터 바로 막고, 내리면 바로 풀어 준다")
    void followsMinimumVersionImmediately() {
        // given
        appVersionPolicyStore.register(Platform.ANDROID, "1.0.0", "1.1.0");
        assertThat(updateRequired("ANDROID", "1.0.0")).isFalse();

        // when -> 캐시를 두지 않아 올린 직후의 구버전 요청도 걸린다
        appVersionPolicyStore.register(Platform.ANDROID, "1.1.0", "1.1.0");

        // then
        assertThat(updateRequired("ANDROID", "1.0.0")).isTrue();
        assertThat(updateRequired("ANDROID", "1.1.0")).isFalse();

        // when -> 잘못 올렸으면 내리는 즉시 업데이트 화면에서 풀려난다
        appVersionPolicyStore.register(Platform.ANDROID, "1.0.0", "1.1.0");

        // then
        assertThat(updateRequired("ANDROID", "1.0.0")).isFalse();
    }

    @Test
    @DisplayName("플랫폼마다 따로 판정한다")
    void judgesPerPlatform() {
        // given -> 안드로이드에만 나온 1.2.0을 iOS 사용자에게 요구하지 않는다
        appVersionPolicyStore.register(Platform.ANDROID, "1.2.0", "1.2.0");
        appVersionPolicyStore.register(Platform.IOS, "1.1.0", "1.1.0");

        // when & then
        assertThat(updateRequired("ANDROID", "1.1.0")).isTrue();
        assertThat(updateRequired("IOS", "1.1.0")).isFalse();
    }

    private boolean updateRequired(String platform, String appVersion) {
        return handler.handle(new CheckAppVersionQuery(platform, appVersion)).updateRequired();
    }
}
