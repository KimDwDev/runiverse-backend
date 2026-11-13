package com.runiverse.running_service.application.recordcard.port.out;

public interface GenerateRecordCardUploadUrlPort {

    // 기록 카드 버킷에 한 번에 올릴 presigned PUT URL — 형식·크기를 서명에 넣어 다른 값으로는 올리지 못한다.
    // 유효 시간은 storage.s3.presigned-url-ttl(초안보다 짧다)
    String generate(String key, String contentType, long sizeBytes);
}
