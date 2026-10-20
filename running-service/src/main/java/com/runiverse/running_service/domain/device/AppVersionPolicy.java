package com.runiverse.running_service.domain.device;

import com.runiverse.running_service.domain.device.exception.PlatformRequiredException;
import com.runiverse.running_service.domain.device.vo.AppVersion;
import com.runiverse.running_service.domain.device.vo.Platform;
import lombok.Builder;
import lombok.Getter;

// 플랫폼마다 몇 버전부터 쓸 수 있고 최신은 몇 버전인가 — 운영이 SQL로 바꾸는 값이라 앱은 읽기만 한다.
// 최소 ≤ 최신은 DB가 막는다
@Getter
public class AppVersionPolicy {

    private final Platform platform;
    private final AppVersion minimumVersion;
    private final AppVersion latestVersion;

    @Builder
    private AppVersionPolicy(Platform platform, String minimumVersion, String latestVersion) {
        if (platform == null) {
            throw new PlatformRequiredException();
        }
        this.platform = platform;
        this.minimumVersion = new AppVersion(minimumVersion);
        this.latestVersion = new AppVersion(latestVersion);
    }

    public boolean requiresUpdate(AppVersion appVersion) {
        return appVersion.isLowerThan(minimumVersion);
    }
}
