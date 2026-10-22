package com.runiverse.running_service.application.relationship.port.out;

import com.runiverse.running_service.domain.common.vo.UserId;

public interface ExistsUserPort {

    // users 행이 있으면 있는 사용자다 — 탈퇴하면 행이 지워져 없는 사용자와 같다
    boolean existsUser(UserId userId);
}
