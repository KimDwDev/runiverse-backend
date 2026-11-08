package com.runiverse.running_service.domain.recordcard;

// 카드에 새로 넣을 사진 하나 — 사진 key는 업로드한 것, 음악 key는 서버가 정해 둔 것이다
public record RecordCardImageDraft(String imageKey, String musicKey) {

}
