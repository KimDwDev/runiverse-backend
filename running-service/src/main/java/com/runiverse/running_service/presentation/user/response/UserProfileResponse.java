package com.runiverse.running_service.presentation.user.response;

import com.runiverse.running_service.domain.relationship.vo.RelationshipType;

import java.util.UUID;

public record UserProfileResponse(
        UUID userId,
        boolean isMe,
        String nickname,
        String profileImageUrl,
        String introduction,
        long friendCount,
        RelationshipType relationshipType,   // 조회자 기준 지금 관계. 본인 프로필이면 null
        // LEGACY(v1_1): 1.1.x 앱용 옛 필드 — 최소 버전을 1.2.0으로 올린 뒤 아래 생성자와 함께 지운다.
        // 같은 경로라 legacy 폴더에 둘 수 없다. 1.1.1 서버처럼 남의 프로필이면 "ACCEPTED" 고정, 본인이면 null
        String friendStatus
) {

    // LEGACY(v1_1): 1.1.1 서버가 실제 관계와 무관하게 내리던 값이다
    private static final String LEGACY_FRIEND_STATUS = "ACCEPTED";

    // LEGACY(v1_1): friendStatus를 여기서 채워 호출하는 쪽은 옛 필드를 모르게 한다
    public UserProfileResponse(
            UUID userId,
            boolean isMe,
            String nickname,
            String profileImageUrl,
            String introduction,
            long friendCount,
            RelationshipType relationshipType
    ) {
        this(userId, isMe, nickname, profileImageUrl, introduction, friendCount, relationshipType,
                isMe ? null : LEGACY_FRIEND_STATUS);
    }
}
