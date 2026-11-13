package com.runiverse.running_service.application.recordcard.common;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@ConfigurationProperties(prefix = "record-card.draft")
@Validated
public record RecordCardDraftProperties(
        // 초안 유효 시간 — 이 안에 9-2로 확정해야 한다. 조각 URL도 같은 시간을 준다(큰 파일은 모바일에서 오래 걸린다).
        // 한 번에 올리는 URL은 이보다 짧은 storage.s3.presigned-url-ttl을 따른다
        @NotNull Duration ttl,
        // 사진 한 장의 최대 크기 — 넘으면 9-1이 400으로 거절한다.
        // 응답 문구(api-spec 9-1 "이미지는 100MB 이하만…")에 이 값이 박혀 있어 바꾸면 문구·명세도 함께 고친다
        @NotNull DataSize maxImageSize,
        // 이 크기 이하는 URL 하나로 한 번에 올리고, 넘으면 이 크기씩 조각으로 나눠 올린다 — 마지막 조각만 이보다 작다
        @NotNull DataSize partSize
) {

    // S3는 마지막을 뺀 조각이 5MiB보다 작으면 합치기(CompleteMultipartUpload)를 거절한다
    private static final DataSize S3_MIN_PART_SIZE = DataSize.ofMegabytes(5);
    // S3 Multipart Upload 한 건의 조각 수 상한
    private static final long S3_MAX_PART_COUNT = 10_000;

    // null 검사는 @NotNull이 따로 보고한다 — 여기서 터지면 원인이 가려진다
    @AssertTrue(message = "초안 유효 시간은 0보다 커야 한다")
    public boolean isTtlPositive() {
        return ttl == null || ttl.isPositive();
    }

    // 확정 때 S3가 거절하면 사용자가 사진을 다 올린 뒤라 — 기동 때 막는다
    @AssertTrue(message = "조각 크기는 S3 최소 조각 크기(5MB) 이상이어야 한다")
    public boolean isPartSizeAtLeastS3Minimum() {
        return partSize == null || partSize.compareTo(S3_MIN_PART_SIZE) >= 0;
    }

    @AssertTrue(message = "최대 크기를 조각 크기로 나눈 조각 수가 S3 상한(10,000)을 넘는다")
    public boolean isPartCountWithinS3Limit() {
        if (maxImageSize == null || partSize == null || partSize.toBytes() <= 0) {
            return true;
        }
        return Math.ceilDiv(maxImageSize.toBytes(), partSize.toBytes()) <= S3_MAX_PART_COUNT;
    }
}
