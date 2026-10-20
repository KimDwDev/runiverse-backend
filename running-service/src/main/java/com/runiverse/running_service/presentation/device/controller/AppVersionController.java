package com.runiverse.running_service.presentation.device.controller;

import com.runiverse.running_service.application.device.port.in.GetAppVersionUsecase;
import com.runiverse.running_service.application.device.query.version.GetAppVersionQuery;
import com.runiverse.running_service.application.device.query.version.GetAppVersionResult;
import com.runiverse.running_service.presentation.device.request.AppVersionRequest;
import com.runiverse.running_service.presentation.device.response.AppVersionResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("app-versions")
@RequiredArgsConstructor
public class AppVersionController {

    private final GetAppVersionUsecase getAppVersionUsecase;

    @GetMapping
    public ResponseEntity<AppVersionResponse> getAppVersion(@Valid @ModelAttribute AppVersionRequest request) {
        GetAppVersionResult result = getAppVersionUsecase.handle(new GetAppVersionQuery(request.platform()));
        return ResponseEntity.ok(new AppVersionResponse(result.minimumVersion(), result.latestVersion()));
    }
}
