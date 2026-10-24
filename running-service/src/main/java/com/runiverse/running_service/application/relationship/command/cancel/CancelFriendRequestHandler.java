package com.runiverse.running_service.application.relationship.command.cancel;

import com.runiverse.running_service.application.relationship.exception.FriendRequestNotFoundException;
import com.runiverse.running_service.application.relationship.port.in.CancelFriendRequestUsecase;
import com.runiverse.running_service.application.relationship.port.out.LoadUserRelationshipPort;
import com.runiverse.running_service.application.relationship.port.out.SaveUserRelationshipPort;
import com.runiverse.running_service.application.relationship.port.out.UserRelationshipPair;
import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.domain.relationship.UserRelationship;
import com.runiverse.running_service.domain.relationship.vo.RelationshipType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 보낸 쪽이 부르면 취소, 받은 쪽이 부르면 거절이다 — 하는 일이 같아 하나로 두고 로그로만 가른다
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class CancelFriendRequestHandler implements CancelFriendRequestUsecase {

    private final LoadUserRelationshipPort loadUserRelationshipPort;
    private final SaveUserRelationshipPort saveUserRelationshipPort;

    @Override
    public void handle(CancelFriendRequestCommand command) {
        UserId userId = new UserId(command.userId());
        UserId targetId = new UserId(command.targetId());

        // 1. 두 행을 함께 잠가 읽는다 — 그사이 상대가 수락하면 친구가 된 상태를 읽어 404가 된다
        UserRelationshipPair pair = loadUserRelationshipPort.loadPairForUpdate(userId, targetId);
        UserRelationship mine = pair.findMine()
                .orElseThrow(() -> notFound(targetId));

        // 2. 대기 중인 요청만 지운다 — 친구·끝난 관계는 지울 요청이 없는 것이다
        RelationshipType expectedTheirs = switch (mine.getType()) {
            case FRIEND_REQUEST_SENT -> RelationshipType.FRIEND_REQUEST_RECEIVED;
            case FRIEND_REQUEST_RECEIVED -> RelationshipType.FRIEND_REQUEST_SENT;
            default -> throw notFound(targetId);
        };

        // 3. 상대 행은 반대 방향 요청이어야 한다 — 아니면 짝이 깨진 데이터라 500이다
        UserRelationship theirs = pair.findTheirs()
                .filter(relationship -> relationship.getType() == expectedTheirs)
                .orElseThrow(() -> new IllegalStateException("친구 요청의 짝이 맞지 않습니다."));

        // 4. 두 행을 함께 NONE으로 되돌린다 — 바꾸기 전에 취소인지 거절인지 봐 둔다
        boolean cancelled = mine.getType() == RelationshipType.FRIEND_REQUEST_SENT;
        mine.end();
        theirs.end();
        saveUserRelationshipPort.save(mine);
        saveUserRelationshipPort.save(theirs);

        if (cancelled) {
            log.info("[관계] 친구 요청 취소 성공 - targetUserId={}", targetId.value());
        } else {
            log.info("[관계] 친구 요청 거절 성공 - targetUserId={}", targetId.value());
        }
    }

    private FriendRequestNotFoundException notFound(UserId targetId) {
        log.info("[관계] 친구 요청 취소·거절 실패: 대기 중인 요청 없음 - targetUserId={}", targetId.value());
        return new FriendRequestNotFoundException();
    }
}
