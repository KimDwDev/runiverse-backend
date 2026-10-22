package com.runiverse.running_service.infrastructure.persistence.relationship;

import com.runiverse.running_service.application.relationship.port.out.LoadUserRelationshipPort;
import com.runiverse.running_service.application.relationship.port.out.SaveUserRelationshipPort;
import com.runiverse.running_service.application.relationship.port.out.UserRelationshipPair;
import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.domain.relationship.UserRelationship;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class UserRelationshipPersistenceAdapter implements LoadUserRelationshipPort, SaveUserRelationshipPort {

    private final EntityManager entityManager;

    // 관계 행을 바꾸는 경로는 모두 이 메서드로 잠근다 — 따로 잠그는 경로가 생기면 잠금 순서가 엇갈려 교착에 빠진다.
    // 두 행을 한 쿼리로 id 순서대로 잠가 누가 호출하든 순서가 같다. users와 JOIN하면 그 행까지 잠기므로 JOIN하지 않는다
    @Override
    public UserRelationshipPair loadPairForUpdate(UserId userId, UserId targetId) {
        List<UserRelationshipJpaEntity> rows = entityManager.createQuery("""
                        SELECT r
                        FROM UserRelationshipJpaEntity r
                        WHERE (r.userId = :userId AND r.targetId = :targetId)
                           OR (r.userId = :targetId AND r.targetId = :userId)
                        ORDER BY r.userRelationshipId
                        """, UserRelationshipJpaEntity.class)
                .setParameter("userId", userId.value())
                .setParameter("targetId", targetId.value())
                // 상대가 커밋할 때까지 기다렸다 바뀐 행을 읽는다 — 취소된 요청을 수락하지 않게 한다
                .setLockMode(LockModeType.PESSIMISTIC_WRITE)
                .getResultList();

        return new UserRelationshipPair(
                findOwnedBy(rows, userId.value()),
                findOwnedBy(rows, targetId.value()));
    }

    @Override
    public void save(UserRelationship relationship) {
        if (relationship.isNew()) {
            entityManager.persist(UserRelationshipJpaEntity.create(
                    relationship.getUserId().value(),
                    relationship.getTargetId().value(),
                    relationship.getType()));
            return;
        }
        Long relationshipId = relationship.getUserRelationshipId().orElseThrow().value();
        // 잠금 조회로 이미 영속 상태라 다시 조회하지 않는다 — 더티 체킹이 UPDATE를 만든다
        UserRelationshipJpaEntity entity = entityManager.find(UserRelationshipJpaEntity.class, relationshipId);
        entity.changeType(relationship.getType());
    }

    private static UserRelationship findOwnedBy(List<UserRelationshipJpaEntity> rows, UUID ownerId) {
        return rows.stream()
                .filter(row -> row.getUserId().equals(ownerId))
                .findFirst()
                .map(UserRelationshipPersistenceAdapter::toDomain)
                .orElse(null);
    }

    private static UserRelationship toDomain(UserRelationshipJpaEntity entity) {
        return UserRelationship.builder()
                .userRelationshipId(entity.getUserRelationshipId())
                .userId(entity.getUserId())
                .targetId(entity.getTargetId())
                .type(entity.getType())
                .build();
    }
}
