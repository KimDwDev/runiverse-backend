package com.runiverse.running_service.application.appversion.query.version;

import com.runiverse.running_service.application.appversion.port.in.GetAppVersionUsecase;
import com.runiverse.running_service.application.appversion.port.out.LoadAppVersionPolicyPort;
import com.runiverse.running_service.domain.appversion.AppVersionPolicy;
import com.runiverse.running_service.domain.appversion.vo.Platform;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GetAppVersionHandler implements GetAppVersionUsecase {

    private final LoadAppVersionPolicyPort loadAppVersionPolicyPort;

    @Override
    public GetAppVersionResult handle(GetAppVersionQuery query) {
        Platform platform = Platform.from(query.platform());

        // 배포 SQL이 두 플랫폼 행을 미리 넣는다 — 없으면 운영 실수라 500으로 드러낸다
        AppVersionPolicy policy = loadAppVersionPolicyPort.load(platform)
                .orElseThrow(() -> new IllegalStateException("앱 버전 행이 없다 — platform=" + platform));
        return new GetAppVersionResult(
                policy.getMinimumVersion().value(), policy.getLatestVersion().value());
    }
}
