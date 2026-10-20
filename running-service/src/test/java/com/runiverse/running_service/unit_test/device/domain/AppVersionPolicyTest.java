package com.runiverse.running_service.unit_test.device.domain;

import com.runiverse.running_service.domain.device.AppVersionPolicy;
import com.runiverse.running_service.domain.device.exception.InvalidVersionNameFormatException;
import com.runiverse.running_service.domain.device.exception.PlatformRequiredException;
import com.runiverse.running_service.domain.device.vo.Platform;
import com.runiverse.running_service.domain.device.vo.VersionName;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class AppVersionPolicyTest {

    private static AppVersionPolicy policy(String minimumVersion, String latestVersion) {
        return AppVersionPolicy.builder()
                .platform(Platform.ANDROID)
                .minimumVersion(minimumVersion)
                .latestVersion(latestVersion)
                .build();
    }

    @Test
    @DisplayName("플랫폼과 두 버전을 담는다")
    void createPolicySuccess() {
        // when
        AppVersionPolicy policy = policy("1.1.0", "1.2.0");

        // then
        assertThat(policy.getPlatform()).isEqualTo(Platform.ANDROID);
        assertThat(policy.getMinimumVersion()).isEqualTo(new VersionName("1.1.0"));
        assertThat(policy.getLatestVersion()).isEqualTo(new VersionName("1.2.0"));
    }

    @Test
    @DisplayName("최소 버전보다 낮은 앱은 업데이트가 필요하다")
    void lowerThanMinimumRequiresUpdate() {
        // when & then
        assertThat(policy("1.1.0", "1.2.0").requiresUpdate(new VersionName("1.0.9"))).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"1.1.0", "1.1.5", "1.2.0", "2.0.0"})
    @DisplayName("최소 버전 이상이면 최신보다 낮아도 업데이트가 필요 없다")
    void atOrAboveMinimumDoesNotRequireUpdate(String versionName) {
        // when & then -> 최신 미만은 닫을 수 있는 안내라 앱이 판정한다
        assertThat(policy("1.1.0", "1.2.0").requiresUpdate(new VersionName(versionName))).isFalse();
    }

    @Test
    @DisplayName("플랫폼이 없으면 필수 예외가 발생한다")
    void nullPlatformFails() {
        // when & then
        assertThatThrownBy(() -> AppVersionPolicy.builder()
                .minimumVersion("1.0.0")
                .latestVersion("1.0.0")
                .build())
                .isInstanceOf(PlatformRequiredException.class);
    }

    @Test
    @DisplayName("버전 형식이 틀리면 VO가 막는다")
    void invalidVersionFails() {
        // when & then -> DB CHECK가 막지만 읽어 온 값도 VO를 거친다
        assertThatThrownBy(() -> policy("1.1", "1.2.0"))
                .isInstanceOf(InvalidVersionNameFormatException.class);
    }
}
