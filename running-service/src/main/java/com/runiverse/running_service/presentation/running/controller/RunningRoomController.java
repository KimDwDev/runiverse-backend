package com.runiverse.running_service.presentation.running.controller;

import com.runiverse.running_service.application.invite.command.accept.AcceptInvitationCommand;
import com.runiverse.running_service.application.invite.command.accept.AcceptInvitationResult;
import com.runiverse.running_service.application.invite.command.decline.DeclineInvitationCommand;
import com.runiverse.running_service.application.invite.command.open.OpenInviteRoomCommand;
import com.runiverse.running_service.application.invite.command.open.OpenInviteRoomResult;
import com.runiverse.running_service.application.invite.command.send.InviteFriendsCommand;
import com.runiverse.running_service.application.invite.command.send.InviteFriendsResult;
import com.runiverse.running_service.application.invite.port.in.AcceptInvitationUsecase;
import com.runiverse.running_service.application.invite.port.in.DeclineInvitationUsecase;
import com.runiverse.running_service.application.invite.port.in.InviteFriendsUsecase;
import com.runiverse.running_service.application.invite.port.in.OpenInviteRoomUsecase;
import com.runiverse.running_service.application.running.command.solo.OpenSoloRoomCommand;
import com.runiverse.running_service.application.running.command.solo.OpenSoloRoomResult;
import com.runiverse.running_service.application.running.port.in.GetRunningResultsUsecase;
import com.runiverse.running_service.application.running.port.in.GetRunningSplitResultsUsecase;
import com.runiverse.running_service.application.running.port.in.OpenSoloRoomUsecase;
import com.runiverse.running_service.application.running.query.result.GetRunningResultsQuery;
import com.runiverse.running_service.application.running.query.result.GetRunningResultsResult;
import com.runiverse.running_service.application.running.query.split.GetRunningSplitResultsQuery;
import com.runiverse.running_service.application.running.query.split.GetRunningSplitResultsResult;
import com.runiverse.running_service.presentation.running.request.InviteFriendsRequest;
import com.runiverse.running_service.presentation.running.request.OpenInviteRoomRequest;
import com.runiverse.running_service.presentation.running.response.AcceptInvitationResponse;
import com.runiverse.running_service.presentation.running.response.InviteFriendsResponse;
import com.runiverse.running_service.presentation.running.response.OpenInviteRoomResponse;
import com.runiverse.running_service.presentation.running.response.RunningResultsResponse;
import com.runiverse.running_service.presentation.running.response.RunningSplitResultsResponse;
import com.runiverse.running_service.presentation.running.response.SoloRunningStartResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("running-rooms")
@RequiredArgsConstructor
public class RunningRoomController {

    private final OpenSoloRoomUsecase startSoloRunningUsecase;
    private final OpenInviteRoomUsecase openInviteRoomUsecase;
    private final InviteFriendsUsecase inviteFriendsUsecase;
    private final AcceptInvitationUsecase acceptInvitationUsecase;
    private final DeclineInvitationUsecase declineInvitationUsecase;
    private final GetRunningResultsUsecase getRunningResultsUsecase;
    private final GetRunningSplitResultsUsecase getRunningSplitResultsUsecase;

    @PostMapping("/solo")
    public ResponseEntity<SoloRunningStartResponse> startSolo(@AuthenticationPrincipal Jwt jwt) {
        UUID userId = UUID.fromString(jwt.getSubject());
        OpenSoloRoomResult result = startSoloRunningUsecase.handle(
                new OpenSoloRoomCommand(userId));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new SoloRunningStartResponse(result.runningRoomId()));
    }

    @PostMapping("/invite")
    public ResponseEntity<OpenInviteRoomResponse> openInvite(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody OpenInviteRoomRequest request
    ) {
        UUID userId = UUID.fromString(jwt.getSubject());
        OpenInviteRoomResult result = openInviteRoomUsecase.handle(new OpenInviteRoomCommand(
                userId, request.scheduledStartAt(), request.targetDistanceMeters()));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new OpenInviteRoomResponse(result.runningRoomId()));
    }

    // 초대는 방의 하위 리소스다 — 받는 사람의 수락·거절은 토큰 주체가 곧 받는 사람이라 본문이 없다
    @PostMapping("/{runningRoomId}/invitations")
    public ResponseEntity<InviteFriendsResponse> inviteFriends(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable @Min(value = 1, message = "입력값이 올바르지 않습니다.") Long runningRoomId,
            @Valid @RequestBody InviteFriendsRequest request
    ) {
        UUID userId = UUID.fromString(jwt.getSubject());
        InviteFriendsResult result = inviteFriendsUsecase.handle(
                new InviteFriendsCommand(userId, runningRoomId, request.inviteeIds()));
        return ResponseEntity.status(HttpStatus.CREATED).body(InviteFriendsResponse.from(result));
    }

    @PostMapping("/{runningRoomId}/invitations/accept")
    public ResponseEntity<AcceptInvitationResponse> acceptInvitation(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable @Min(value = 1, message = "입력값이 올바르지 않습니다.") Long runningRoomId
    ) {
        UUID userId = UUID.fromString(jwt.getSubject());
        AcceptInvitationResult result = acceptInvitationUsecase.handle(
                new AcceptInvitationCommand(userId, runningRoomId));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new AcceptInvitationResponse(result.runningRoomId()));
    }

    @PostMapping("/{runningRoomId}/invitations/decline")
    public ResponseEntity<Void> declineInvitation(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable @Min(value = 1, message = "입력값이 올바르지 않습니다.") Long runningRoomId
    ) {
        UUID userId = UUID.fromString(jwt.getSubject());
        declineInvitationUsecase.handle(new DeclineInvitationCommand(userId, runningRoomId));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{runningRoomId}/results")
    public ResponseEntity<RunningResultsResponse> getResults(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable @Min(value = 1, message = "입력값이 올바르지 않습니다.") Long runningRoomId
    ) {
        // 조회자는 토큰에서만 온다 — 클라가 보낸 값이면 남의 결과를 볼 수 있다
        UUID userId = UUID.fromString(jwt.getSubject());
        GetRunningResultsResult result = getRunningResultsUsecase.handle(
                new GetRunningResultsQuery(runningRoomId, userId));
        return ResponseEntity.ok(RunningResultsResponse.from(result));
    }

    @GetMapping("/{runningRoomId}/split-results")
    public ResponseEntity<RunningSplitResultsResponse> getSplitResults(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable @Min(value = 1, message = "입력값이 올바르지 않습니다.") Long runningRoomId
    ) {
        UUID userId = UUID.fromString(jwt.getSubject());
        GetRunningSplitResultsResult result = getRunningSplitResultsUsecase.handle(
                new GetRunningSplitResultsQuery(runningRoomId, userId));
        return ResponseEntity.ok(RunningSplitResultsResponse.from(result));
    }
}
