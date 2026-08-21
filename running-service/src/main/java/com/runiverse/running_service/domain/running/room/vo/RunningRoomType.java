package com.runiverse.running_service.domain.running.room.vo;

import com.runiverse.running_service.domain.running.room.exception.RunningRoomTypeRequiredException;
import com.runiverse.running_service.domain.running.room.exception.UnsupportedRunningRoomTypeException;

import java.util.Locale;

public enum RunningRoomType {
    SOLO, // 혼자 러닝
    MATCH, // 매칭 러닝
    INVITE; // 초대 러닝

    // 솔로는 모집 단계가 없어 STARTED로 태어난다 - 방 생성이 이 규칙을 여기서만 읽는다
    public static RunningRoomType from(String value) {
        if (value == null) {
            throw new RunningRoomTypeRequiredException();
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            throw new RunningRoomTypeRequiredException();
        }
        try {
            return valueOf(normalized);
        } catch (IllegalArgumentException e) {
            throw new UnsupportedRunningRoomTypeException();
        }
    }

    // 솔로는 모집 단계가 없어 STARTED로 태어난다 — 방 생성이 이 규칙을 여기서만 읽는다
    public RunningRoomStatus initialStatus() {
        return this == SOLO ? RunningRoomStatus.STARTED : RunningRoomStatus.MATCHING;
    }

    // 모집 마감(close_at)·후보 스캔 대상인지
    public boolean isMatchable() {
        return this == MATCH;
    }
}
