package com.runiverse.running_service.application.match.port.out;

import java.time.LocalDateTime;
import java.util.List;

public interface LoadMatchCandidatesPort {

    // 같은 슬롯·거리에서 모집 중이고 자리가 남은 방(erd 인덱스 참고).
    // 솔로·초대 방은 type으로 배제된다. 페이스 근접·최종 자격은 애그리거트가 다시 판정한다
    List<MatchCandidate> loadCandidates(LocalDateTime startAt, int targetDistanceMeters);
}
