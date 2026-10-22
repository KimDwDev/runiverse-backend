package com.runiverse.running_service.application.relationship.command.accept;

import com.runiverse.running_service.application.relationship.exception.FriendRequestNotFoundException;
import com.runiverse.running_service.application.relationship.port.in.AcceptFriendRequestUsecase;
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

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class AcceptFriendRequestHandler implements AcceptFriendRequestUsecase {

    private final LoadUserRelationshipPort loadUserRelationshipPort;
    private final SaveUserRelationshipPort saveUserRelationshipPort;

    @Override
    public AcceptFriendRequestResult handle(AcceptFriendRequestCommand command) {
        UserId userId = new UserId(command.userId());
        UserId requesterId = new UserId(command.requesterId());

        // 1. 두 행을 함께 잠가 읽는다 — 그사이 상대가 요청을 취소하면 취소된 상태를 읽게 된다
        UserRelationshipPair pair = loadUserRelationshipPort.loadPairForUpdate(userId, requesterId);
        UserRelationship mine = pair.findMine()
                .orElseThrow(() -> notFound(requesterId));

        // 2. 이미 친구면 그대로 돌려준다 — 응답을 못 받은 앱의 재요청이다
        if (mine.getType() == RelationshipType.FRIEND) {
            return new AcceptFriendRequestResult(RelationshipType.FRIEND);
        }

        // 3. 내가 받은 요청만 수락한다 — 내가 보낸 요청·끝난 관계는 수락할 요청이 없는 것이다
        if (mine.getType() != RelationshipType.FRIEND_REQUEST_RECEIVED) {
            throw notFound(requesterId);
        }

        // 4. 상대 행은 반드시 보낸 요청이다 — 아니면 짝이 깨진 데이터라 사용자 잘못이 아니다
        UserRelationship theirs = pair.findTheirs()
                .filter(relationship -> relationship.getType() == RelationshipType.FRIEND_REQUEST_SENT)
                .orElseThrow(() -> new IllegalStateException("친구 요청의 짝이 맞지 않습니다."));

        // 5. 두 행을 함께 친구로 바꾼다
        mine.accept();
        theirs.accept();
        saveUserRelationshipPort.save(mine);
        saveUserRelationshipPort.save(theirs);

        log.info("[관계] 친구 요청 수락 성공 - targetUserId={}", requesterId.value());
        return new AcceptFriendRequestResult(RelationshipType.FRIEND);
    }

    private FriendRequestNotFoundException notFound(UserId requesterId) {
        log.info("[관계] 친구 요청 수락 실패: 받은 요청 없음 - targetUserId={}", requesterId.value());
        return new FriendRequestNotFoundException();
    }
}
