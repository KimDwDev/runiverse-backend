package com.runiverse.running_service.application.match.port.out;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

public interface CountJoinableWaitingPlayersPort {

    // 그날 모집 중(type='MATCH' AND status='MATCHING')인 방의 인원을 거리별·시작 시각별로 합산한다 — 키는 목표 거리(m)다.
    // 자리가 남은 방이 하나도 없는 칸은 키가 없다 — 지금 신청하면 새 방이 열릴 뿐이라 함께 기다리는 사람으로 세지 않는다
    Map<Integer, Map<LocalDateTime, Integer>> countJoinableWaitingPlayers(LocalDate date);
}
