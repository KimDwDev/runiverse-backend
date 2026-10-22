package com.runiverse.running_service.presentation.relationship.controller;

import com.runiverse.running_service.application.relationship.command.accept.AcceptFriendRequestCommand;
import com.runiverse.running_service.application.relationship.command.accept.AcceptFriendRequestResult;
import com.runiverse.running_service.application.relationship.port.in.AcceptFriendRequestUsecase;
import com.runiverse.running_service.presentation.relationship.response.RelationTypeResponse;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

// 경로는 /users 아래지만 다루는 리소스가 관계라 relationship에 둔다
@RestController
@RequestMapping("users")
@RequiredArgsConstructor
public class RelationshipController {

    // UserId VO가 받는 UUIDv7만 — UUID 타입으로 받으면 1-1-1-1-1 같은 값도 변환된다
    private static final String USER_ID_FORMAT =
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-7[0-9a-fA-F]{3}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$";
    private final AcceptFriendRequestUsecase acceptFriendRequestUsecase;

    // 경로의 userId는 요청을 보낸 사람이다 — 수락하는 사람은 토큰에서만 온다
    @PutMapping("/{userId}/friend-request")
    public ResponseEntity<RelationTypeResponse> acceptFriendRequest(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable @Pattern(regexp = USER_ID_FORMAT, message = "입력값이 올바르지 않습니다.") String userId
    ) {
        AcceptFriendRequestResult result = acceptFriendRequestUsecase.handle(new AcceptFriendRequestCommand(
                UUID.fromString(jwt.getSubject()),
                UUID.fromString(userId)));
        return ResponseEntity.ok(new RelationTypeResponse(result.relationType().name()));
    }
}
