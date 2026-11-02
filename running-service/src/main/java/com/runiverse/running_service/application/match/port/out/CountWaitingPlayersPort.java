package com.runiverse.running_service.application.match.port.out;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

public interface CountWaitingPlayersPort {

    // 그날 모집 중(type='MATCH' AND status='MATCHING')인 방의 인원을 거리별·시작 시각별로 합산한다 — 키는 목표 거리(m)다.
    // 대기자가 없는 거리·시각은 키가 없다 — 0으로 채우는 건 슬롯을 만드는 쪽이 한다
    Map<Integer, Map<LocalDateTime, Integer>> countWaitingPlayers(LocalDate date);
}
