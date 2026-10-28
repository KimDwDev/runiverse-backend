package com.runiverse.running_service.application.notification.port.out;

import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.domain.relationship.vo.RelationshipType;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

public interface LoadRelationshipTypesPort {

    // 조회하는 사람이 본 각 사람과의 지금 관계 — 알림을 일으킨 사람들을 한 번에 읽는다.
    // 관계 행은 처음 요청할 때 생기므로 행이 없는 사람은 결과에서 빠진다. 호출자가 NONE으로 본다
    Map<UUID, RelationshipType> loadTypes(UserId viewerId, Collection<UUID> targetIds);
}
