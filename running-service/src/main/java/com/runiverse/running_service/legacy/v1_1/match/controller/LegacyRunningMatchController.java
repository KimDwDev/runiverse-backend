package com.runiverse.running_service.legacy.v1_1.match.controller;

import com.runiverse.running_service.application.match.command.apply.ApplyMatchCommand;
import com.runiverse.running_service.application.match.command.apply.ApplyMatchResult;
import com.runiverse.running_service.application.match.command.cancel.CancelMatchCommand;
import com.runiverse.running_service.application.match.exception.InvalidMatchOptionException;
import com.runiverse.running_service.application.match.port.in.ApplyMatchUsecase;
import com.runiverse.running_service.application.match.port.in.CancelMatchUsecase;
import com.runiverse.running_service.application.match.port.in.GetMatchSlotsUsecase;
import com.runiverse.running_service.application.match.query.slot.GetMatchSlotsQuery;
import com.runiverse.running_service.application.match.query.slot.GetMatchSlotsResult;
import com.runiverse.running_service.legacy.v1_1.match.request.LegacyMatchSlotsRequest;
import com.runiverse.running_service.legacy.v1_1.match.response.LegacyMatchSlotsResponse;
import com.runiverse.running_service.presentation.match.request.ApplyMatchRequest;
import com.runiverse.running_service.presentation.match.response.ApplyMatchResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;
import java.util.stream.Collectors;

// 1.1.x 앱 호환 — 1.2.0에서 /match-rooms로 옮긴 매칭 REST를 옛 경로·형식으로 받는다.
// 동작은 1.2.0 유스케이스를 그대로 부른다. 최소 버전을 1.2.0 이상으로 올리면 legacy/v1_1과 함께 지운다
@Slf4j
@RestController
@RequestMapping("running-matches")
@RequiredArgsConstructor
public class LegacyRunningMatchController {

    private final GetMatchSlotsUsecase getMatchSlotsUsecase;
    private final ApplyMatchUsecase applyMatchUsecase;
    private final CancelMatchUsecase cancelMatchUsecase;

    // 1.2.0은 모든 거리를 내린다 — 그중 요청한 거리 하나만 옛 형식으로 내린다.
    // 선택지 밖 거리는 1.1.1 서버처럼 400 INVALID_REQUEST로 거절한다
    @GetMapping("slots")
    public ResponseEntity<LegacyMatchSlotsResponse> getSlots(@Valid @ModelAttribute LegacyMatchSlotsRequest request) {
        GetMatchSlotsResult result = getMatchSlotsUsecase.handle(new GetMatchSlotsQuery(request.dateOrToday()));
        int distanceMeters = request.distanceMeters();
        return result.distances().stream()
                .filter(distanceSlots -> distanceSlots.targetDistanceMeters() == distanceMeters)
                .findFirst()
                .map(LegacyMatchSlotsResponse::from)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> invalidDistance(result, distanceMeters));
    }

    @PostMapping
    public ResponseEntity<ApplyMatchResponse> apply(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody ApplyMatchRequest request
    ) {
        UUID userId = UUID.fromString(jwt.getSubject());
        ApplyMatchResult result = applyMatchUsecase.handle(new ApplyMatchCommand(
                userId, request.scheduledStartAt(), request.targetDistanceMeters()));
        return ResponseEntity.status(HttpStatus.CREATED).body(ApplyMatchResponse.from(result));
    }

    // 1.2.0의 POST /match-rooms/leave와 같다 — 대기 취소와 확정 후 나가기를 겸한다
    @DeleteMapping
    public ResponseEntity<Void> cancel(@AuthenticationPrincipal Jwt jwt) {
        cancelMatchUsecase.handle(new CancelMatchCommand(UUID.fromString(jwt.getSubject())));
        return ResponseEntity.noContent().build();
    }

    // 문구는 1.1.1 서버(MatchSlotPolicy.validateDistance)와 같다 — 선택지는 조회 결과의 거리로 만든다
    private static InvalidMatchOptionException invalidDistance(GetMatchSlotsResult result, int distanceMeters) {
        // 사용자가 고른 값의 문제라 INFO다. userId는 MDC가 붙인다
        log.info("[매칭] 슬롯 조회 실패: 선택지 밖 - distanceM={}", distanceMeters);
        return new InvalidMatchOptionException("목표 거리는 %s 중 하나여야 합니다.".formatted(
                result.distances().stream()
                        .map(distanceSlots -> String.valueOf(distanceSlots.targetDistanceMeters()))
                        .collect(Collectors.joining(", "))));
    }
}
