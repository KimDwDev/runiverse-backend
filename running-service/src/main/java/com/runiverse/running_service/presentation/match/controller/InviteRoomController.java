package com.runiverse.running_service.presentation.match.controller;

import com.runiverse.running_service.application.match.command.inviteaccept.AcceptInvitationCommand;
import com.runiverse.running_service.application.match.command.inviteaccept.AcceptInvitationResult;
import com.runiverse.running_service.application.match.command.invitedecline.DeclineInvitationCommand;
import com.runiverse.running_service.application.match.command.inviteopen.OpenInviteRoomCommand;
import com.runiverse.running_service.application.match.command.inviteopen.OpenInviteRoomResult;
import com.runiverse.running_service.application.match.command.invitesend.InviteFriendsCommand;
import com.runiverse.running_service.application.match.command.invitesend.InviteFriendsResult;
import com.runiverse.running_service.application.match.port.in.AcceptInvitationUsecase;
import com.runiverse.running_service.application.match.port.in.DeclineInvitationUsecase;
import com.runiverse.running_service.application.match.port.in.InviteFriendsUsecase;
import com.runiverse.running_service.application.match.port.in.OpenInviteRoomUsecase;
import com.runiverse.running_service.presentation.match.request.InviteFriendsRequest;
import com.runiverse.running_service.presentation.match.request.OpenInviteRoomRequest;
import com.runiverse.running_service.presentation.match.response.AcceptInvitationResponse;
import com.runiverse.running_service.presentation.match.response.InviteFriendsResponse;
import com.runiverse.running_service.presentation.match.response.OpenInviteRoomResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

// 초대 방의 개설·초대·수락·거절 — 같이 뛸 사람을 모으는 모집 단계라 매칭 도메인이다.
// 경로는 방 리소스 아래(/running-rooms)라 솔로·결과 조회(RunningRoomController)와 접두가 같지만 라우트는 겹치지 않는다.
// 수락·거절은 받는 사람이 토큰 주체라 본문이 없다
@RestController
@RequestMapping("running-rooms")
@RequiredArgsConstructor
public class InviteRoomController {

    private final OpenInviteRoomUsecase openInviteRoomUsecase;
    private final InviteFriendsUsecase inviteFriendsUsecase;
    private final AcceptInvitationUsecase acceptInvitationUsecase;
    private final DeclineInvitationUsecase declineInvitationUsecase;

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
}
