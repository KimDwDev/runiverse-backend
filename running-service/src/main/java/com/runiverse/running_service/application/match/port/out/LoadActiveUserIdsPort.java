package com.runiverse.running_service.application.match.port.out;

import com.runiverse.running_service.domain.common.vo.UserId;

import java.util.Collection;
import java.util.Set;

public interface LoadActiveUserIdsPort {

    // 넘긴 사람 중 활성 신청(running_players.deleted_at IS NULL)이 있는 사람만 돌려준다.
    // 매칭 대기·러닝 중·이미 이 방에 들어옴이 모두 여기 잡혀 초대에서 건너뛴다.
    // 사람마다 existsActive를 부르면 최대 50번 쿼리가 나가 한 번에 본다
    Set<UserId> loadActiveUserIds(Collection<UserId> userIds);
}
