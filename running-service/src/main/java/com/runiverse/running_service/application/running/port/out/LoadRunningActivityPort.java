package com.runiverse.running_service.application.running.port.out;

import java.time.LocalDateTime;
import java.util.List;

// 홈 공지 띠의 숫자들. 완주는 기록이 남은 러닝 중 목표에 닿았거나 목표 없는 솔로를 끝낸 것이고,
// 기간은 기록을 저장한 서버 시각으로 자른다 — 기록의 종료 시각은 단말 시계라 어긋날 수 있다.
// 탈퇴한 사람은 세지 않는다
public interface LoadRunningActivityPort {

    // 러닝 중(RUNNING)이고 startedAfter 뒤에 시작한 사람 수
    int countRunningPlayers(LocalDateTime startedAfter);

    // [fromInclusive, toExclusive)에 저장된 완주 기록, 최신순으로 limit건
    List<FinishedRunRow> loadLatestFinishedRuns(LocalDateTime fromInclusive, LocalDateTime toExclusive, int limit);

    // [fromInclusive, toExclusive)에 완주한 사람 수 — 여러 번 완주해도 한 명이다
    int countFinishers(LocalDateTime fromInclusive, LocalDateTime toExclusive);
}
