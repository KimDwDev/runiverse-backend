package com.runiverse.running_service.application.notification.query.list;

import com.runiverse.running_service.application.common.port.out.LoadPlayerProfilesPort;
import com.runiverse.running_service.application.common.port.out.PlayerProfile;
import com.runiverse.running_service.application.notification.common.NotificationProperties;
import com.runiverse.running_service.application.notification.port.in.GetUserNotificationsUsecase;
import com.runiverse.running_service.application.notification.port.out.CountUnreadUserNotificationsPort;
import com.runiverse.running_service.application.notification.port.out.InvitationSnapshot;
import com.runiverse.running_service.application.notification.port.out.LoadInvitationSnapshotsPort;
import com.runiverse.running_service.application.notification.port.out.LoadRelationshipTypesPort;
import com.runiverse.running_service.application.notification.port.out.LoadUserNotificationsPort;
import com.runiverse.running_service.application.notification.port.out.UserNotificationPage;
import com.runiverse.running_service.application.notification.port.out.UserNotificationRow;
import com.runiverse.running_service.application.user.port.out.GenerateViewUrlPort;
import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.domain.notification.vo.FriendAcceptedPayload;
import com.runiverse.running_service.domain.notification.vo.FriendRequestedPayload;
import com.runiverse.running_service.domain.notification.vo.RunningInvitedPayload;
import com.runiverse.running_service.domain.notification.vo.UserNotificationPayload;
import com.runiverse.running_service.domain.relationship.vo.RelationshipType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GetUserNotificationsHandler implements GetUserNotificationsUsecase {

    private final LoadUserNotificationsPort loadUserNotificationsPort;
    private final CountUnreadUserNotificationsPort countUnreadUserNotificationsPort;
    private final LoadPlayerProfilesPort loadPlayerProfilesPort;
    private final LoadRelationshipTypesPort loadRelationshipTypesPort;
    private final LoadInvitationSnapshotsPort loadInvitationSnapshotsPort;
    private final GenerateViewUrlPort generateViewUrlPort;
    private static final String DELETED_NICKNAME = "탈퇴한 사용자";
    private final NotificationProperties notificationProperties;

    @Override
    public GetUserNotificationsResult handle(GetUserNotificationsQuery query) {
        UserId userId = new UserId(query.userId());
        Long beforeId = query.cursor() == null ? null : NotificationCursor.decode(query.cursor()).beforeId();
        // 목록과 안 읽은 수가 같은 경계를 써야 배지와 목록이 어긋나지 않는다
        LocalDateTime since = LocalDateTime.now().minus(notificationProperties.retention());

        // 1. 한 페이지를 읽는다 — 되살리지 못한 행은 어댑터가 이미 뺐다
        UserNotificationPage page = loadUserNotificationsPort.loadPage(userId, beforeId, since, query.limit());
        List<UserNotificationRow> rows = page.rows();

        // 2. 페이지에 나온 사람·방을 한 번씩만 읽는다 — 항목마다 읽으면 N+1이다
        Set<UUID> actorIds = rows.stream()
                .map(row -> row.payload().actorId().value())
                .collect(Collectors.toSet());
        Set<Long> roomIds = rows.stream()
                .map(row -> roomIdOf(row.payload()))
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<UUID, PlayerProfile> profiles = actorIds.isEmpty() ? Map.of() :
                loadPlayerProfilesPort.loadProfiles(actorIds);
        Map<UUID, RelationshipType> relationships =
                actorIds.isEmpty() ? Map.of() : loadRelationshipTypesPort.loadTypes(userId, actorIds);
        Map<Long, InvitationSnapshot> invitations =
                roomIds.isEmpty() ? Map.of() : loadInvitationSnapshotsPort.loadSnapshots(userId, roomIds);

        // 3. 지금 상태를 붙여 항목을 만든다 — 초대 행이 사라진 초대 알림은 뺀다
        List<GetUserNotificationsResult.Item> items = rows.stream()
                .map(row -> toItem(row, profiles, relationships, invitations))
                .flatMap(Optional::stream)
                .toList();

        // 4. 다음 페이지 기준은 걸러내기 전의 행으로 어댑터가 정했다
        String nextCursor = page.nextBeforeId() == null ? null : new NotificationCursor(page.nextBeforeId()).encode();
        long unreadCount = countUnreadUserNotificationsPort.countUnread(userId, since);
        return new GetUserNotificationsResult(items, nextCursor, unreadCount);
    }

    private Optional<GetUserNotificationsResult.Item> toItem(UserNotificationRow row,
                                                             Map<UUID, PlayerProfile> profiles,
                                                             Map<UUID, RelationshipType> relationships,
                                                             Map<Long, InvitationSnapshot> invitations) {
        GetUserNotificationsResult.Invitation invitation = null;
        Long roomId = roomIdOf(row.payload());
        if (roomId != null) {
            InvitationSnapshot snapshot = invitations.get(roomId);
            if (snapshot == null) {
                // 초대는 받은 사람이 탈퇴할 때만 지워지는데 그때는 알림도 함께 지워진다 — 여기 오면 어긋난 데이터다
                log.warn("[알림] 목록 항목 건너뜀: 초대 행 없음 - notificationId={}, runningRoomId={}",
                        row.userNotificationId(), roomId);
                return Optional.empty();
            }
            invitation = new GetUserNotificationsResult.Invitation(
                    snapshot.runningRoomId(), snapshot.status(),
                    snapshot.scheduledStartAt(), snapshot.targetDistanceMeters());
        }
        return Optional.of(new GetUserNotificationsResult.Item(
                row.userNotificationId(), row.payload().type(), row.read(), row.createdAt(),
                toActor(row.payload().actorId().value(), profiles, relationships), invitation));
    }

    // 프로필이 없으면 탈퇴한 사람이다 — 관계 행도 함께 지워져 관계를 싣지 않는다
    private GetUserNotificationsResult.Actor toActor(UUID actorId,
                                                     Map<UUID, PlayerProfile> profiles,
                                                     Map<UUID, RelationshipType> relationships) {
        PlayerProfile profile = profiles.get(actorId);
        if (profile == null) {
            return new GetUserNotificationsResult.Actor(actorId, DELETED_NICKNAME, null, true, null);
        }
        String imageUrl = profile.profileImageKey() == null ? null :
                generateViewUrlPort.generate(profile.profileImageKey());
        // 관계 행은 처음 요청할 때 생긴다 — 행이 없으면 아무 관계도 아니다
        RelationshipType relationshipType = relationships.getOrDefault(actorId, RelationshipType.NONE);
        return new GetUserNotificationsResult.Actor(
                actorId, profile.nickname(), imageUrl, false, relationshipType);
    }

    // 종류가 늘면 sealed라 여기서 컴파일 에러가 난다 — 방을 가리키는 알림인지 빠뜨리지 않는다
    private static Long roomIdOf(UserNotificationPayload payload) {
        return switch (payload) {
            case FriendRequestedPayload p -> null;
            case FriendAcceptedPayload p -> null;
            case RunningInvitedPayload p -> p.runningRoomId().value();
        };
    }
}
