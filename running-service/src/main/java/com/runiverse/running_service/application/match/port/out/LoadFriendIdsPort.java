package com.runiverse.running_service.application.match.port.out;

import com.runiverse.running_service.domain.common.vo.UserId;

import java.util.Collection;
import java.util.Set;

public interface LoadFriendIdsPort {

    // 넘긴 사람 중 userId와 친구(FRIEND)인 사람만 돌려준다 — 최대 50명을 한 번에 본다.
    // 잠그지 않는다 — 보낸 뒤에 관계가 바뀌어도 초대는 이미 사건이라 되돌리지 않는다
    Set<UserId> loadFriendIds(UserId userId, Collection<UserId> targetIds);
}
