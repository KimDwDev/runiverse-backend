package com.runiverse.running_service.application.app.port.out;

// 앱 스트림이 보내는 이벤트 — 이름이 곧 SSE event 이름이고, 클라는 이 값으로 할 일을 가른다(매칭 스트림과 같다).
// 연결 직후 보내는 스냅샷은 없다 — 끊긴 사이의 알림은 앱이 REST로 다시 받고, 홈 공지는 다음 주기에 다시 온다.
// 알림 이벤트의 data는 셋 다 알림 목록 항목에서 type만 뺀 모양이다 — 클라는 목록 맨 위에 붙이고 배지를 하나 올린다
public enum AppStreamEventType {
    // 누군가 나에게 친구 요청을 보냈다
    FRIEND_REQUESTED,
    // 내가 보낸 친구 요청을 상대가 수락했다
    FRIEND_ACCEPTED,
    // 누군가 나를 초대 방에 초대했다 — 다시 초대도 새로 보낸다
    RUNNING_INVITED,
    // 홈 신청 버튼 위 — 오늘 신청할 수 있는 칸의 대기 인원 합계. 주기마다 붙은 연결 전부에 보낸다
    HOME_WAITING,
    // 홈 위쪽 공지 띠 — 지금 달리는 사람 수와 오늘·어제 완주 소식. 주기마다 붙은 연결 전부에 보낸다
    HOME_ACTIVITY
}
