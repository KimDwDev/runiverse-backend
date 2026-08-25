package com.runiverse.running_service.application.running.port.out;

public interface RunningConnection {

    // 같은 유저의 다른 연결과 구분하는 식별자
    String id();

    // 마지막 연결이 이긴다 - 밀려난 쪽을 닫을 때 사용
    void closeSuperseded();
}
