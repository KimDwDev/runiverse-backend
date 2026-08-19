package com.runiverse.running_service.domain.running.vo;

import com.runiverse.running_service.domain.running.exception.InvalidMemberCountException;
import com.runiverse.running_service.domain.running.exception.RoomIsEmptyException;
import com.runiverse.running_service.domain.running.exception.RoomIsFullException;

public record MemberCount(int current, int max) {

    // 현재 서비스 최대 정원
    public static final int MAX_MEMBER = 4;

    // 최대 인원수가 1보다 작을 수는 없고 현재 인원수가 0보다 작을 수는 없다 4보다
    public MemberCount {
        if (max < 1 || current < 0 || current > max || max > MAX_MEMBER) {
            throw new InvalidMemberCountException();
        }
    }

    public static MemberCount solo() {
        return new MemberCount(1, 1);
    }

    public static MemberCount openWith(int max) {
        return new MemberCount(1, max); // 방은 언제나 1인으로 태어남
    }

    public boolean isFull() {
        return current == max; // 현재가 전체이면 꽉참
    }

    public boolean canJoin() {
        return current < max; // 가입 가능
    }

    public MemberCount join() {
        if (isFull()) {
            throw new RoomIsFullException();
        }
        return new MemberCount(current + 1, max);
    }

    public MemberCount leave() {
        if (current == 0) {
            throw new RoomIsEmptyException();
        }
        return new MemberCount(current - 1, max);
    }

}
