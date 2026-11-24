package com.runiverse.running_service.application.running.port.out;

import com.runiverse.running_service.domain.common.vo.UserId;

import java.util.Optional;

// 끝내기 임대 — 같은 러너의 끝내기가 겹쳐 봉인(분석·S3·날씨)을 두 번 하지 않게 한 줄로 세운다.
// 기록이 두 번 남는 것은 확정의 재확인과 running_records UNIQUE가 막는다. 임대는 헛봉인을 막는 장치다
public interface FinishLeasePort {

    // 비어 있을 때만 잡고, 기다리지 않고 바로 답한다. 잡으면 이 요청만의 토큰을, 남이 쥐고 있으면 empty를 돌려준다.
    // 쥔 것이 없을 때(판정 트랜잭션 앞) 부른다 — 잠금을 쥔 채 여기서 기다리면 임대를 쥔 채 잠금을 기다리는 확정과 교착이다
    Optional<String> tryAcquire(Long runningRoomId, UserId userId);

    // 자기 토큰일 때만 지운다 — TTL을 넘겨 늦게 끝난 요청이 그사이 남이 잡은 임대를 지우지 않게
    void release(Long runningRoomId, UserId userId, String token);
}
