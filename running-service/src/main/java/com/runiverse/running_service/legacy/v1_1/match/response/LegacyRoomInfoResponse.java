package com.runiverse.running_service.legacy.v1_1.match.response;

import com.runiverse.running_service.application.match.port.out.RoomInfo;
import com.runiverse.running_service.domain.running.room.vo.RunningRoomStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

// 1.1.x 앱의 매칭 스트림(GET /running-matches/stream) 방 상태 이벤트 data — 1.1.1 서버 형식 그대로다.
// 1.2.0 RoomInfoResponse에 붙은 type·invitations는 싣지 않는다 — 1.1.x 앱은 매칭 방만 쓰고 그 필드를 모른다
public record LegacyRoomInfoResponse(
        Long runningRoomId,
        RunningRoomStatus status,
        LocalDateTime scheduledStartAt,
        LocalDateTime closeAt,
        Integer targetDistanceMeters,
        Integer teamAveragePaceSecondsPerKm,
        List<PlayerResponse> players
) {

    public static LegacyRoomInfoResponse from(RoomInfo room) {
        return new LegacyRoomInfoResponse(
                room.runningRoomId(),
                room.status(),
                room.scheduledStartAt(),
                room.closeAt(),
                room.targetDistanceMeters(),
                room.teamAveragePaceSecondsPerKm(),
                room.players().stream().map(PlayerResponse::from).toList());
    }

    public record PlayerResponse(
            UUID userId,
            String nickname,
            String profileImageUrl,
            String introduction,
            int averagePaceSecondsPerKm,
            boolean isDeleted
    ) {

        public static PlayerResponse from(RoomInfo.RoomPlayer player) {
            return new PlayerResponse(
                    player.userId(),
                    player.nickname(),
                    player.profileImageUrl(),
                    player.introduction(),
                    player.averagePaceSecondsPerKm(),
                    player.isDeleted());
        }
    }
}
