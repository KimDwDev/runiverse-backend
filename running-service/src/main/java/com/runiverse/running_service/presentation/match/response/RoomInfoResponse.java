package com.runiverse.running_service.presentation.match.response;

import com.runiverse.running_service.application.match.query.roominfo.RoomInfo;
import com.runiverse.running_service.domain.running.room.vo.RunningRoomStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

// 13번 조회의 room과 SSE 세 이벤트의 data가 이 구조를 공유한다(api-spec 5-B).
// 와이어 계약이라 presentation이 갖는다 — SSE 전송도 이걸 실어 보낸다
public record RoomInfoResponse(
        Long runningRoomId,
        RunningRoomStatus status,
        LocalDateTime scheduledStartAt,
        LocalDateTime closeAt,
        Integer targetDistanceMeters,
        Integer teamAveragePaceSecondsPerKm,
        List<PlayerResponse> players
) {

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
