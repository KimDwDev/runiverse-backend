package com.runiverse.running_service.infrastructure.storage;

import com.runiverse.running_service.application.recordcard.port.out.GenerateRecordCardUploadUrlPort;
import com.runiverse.running_service.application.recordcard.port.out.MultipartUpload;
import com.runiverse.running_service.application.recordcard.port.out.StartMultipartUploadPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.UploadPartRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

// 기록 카드 사진·음악 버킷 — 프로필·배경 사진(user-assets)과 버킷을 나눠 AI 서버 권한·수명 주기 규칙을 따로 둔다
@Component
@RequiredArgsConstructor
public class RecordCardS3Adapter implements GenerateRecordCardUploadUrlPort, StartMultipartUploadPort {

    private final S3Presigner s3Presigner;
    private final S3Properties properties;
    private final S3Client s3Client;

    @Override
    public String generate(String key, String contentType, long sizeBytes, Duration urlTtl) {
        // 형식·크기를 서명에 넣어 클라가 다른 값으로 올리지 못하게 막는다
        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(properties.recordCardBucket())
                .key(key)
                .contentType(contentType)
                .contentLength(sizeBytes)
                .build();
        return s3Presigner.presignPutObject(builder -> builder
                        .signatureDuration(urlTtl)
                        .putObjectRequest(putObjectRequest))
                .url()
                .toString();
    }

    @Override
    public MultipartUpload start(String key, String contentType, long fileSizeBytes, long partSizeBytes,
                                 Duration urlTtl) {
        // 1. 나눠 올리기를 시작한다 — 조각에는 Content-Type이 없어 합친 사진의 형식은 여기서 정해진다
        String uploadId = s3Client.createMultipartUpload(request -> request
                        .bucket(properties.recordCardBucket())
                        .key(key)
                        .contentType(contentType))
                .uploadId();

        // 2. 조각마다 URL을 서명한다 — 크기를 서명에 넣어 앱이 다르게 자르면 S3가 거절한다. 마지막 조각만 작다
        int partCount = Math.toIntExact(Math.ceilDiv(fileSizeBytes, partSizeBytes));
        List<MultipartUpload.Part> parts = new ArrayList<>(partCount);
        for (int partNumber = 1; partNumber <= partCount; partNumber++) {
            long offset = (partNumber - 1) * partSizeBytes;
            long contentLength = Math.min(partSizeBytes, fileSizeBytes - offset);
            UploadPartRequest uploadPartRequest = UploadPartRequest.builder()
                    .bucket(properties.recordCardBucket())
                    .key(key)
                    .uploadId(uploadId)
                    .partNumber(partNumber)
                    .contentLength(contentLength)
                    .build();
            String url = s3Presigner.presignUploadPart(builder -> builder
                            .signatureDuration(urlTtl)
                            .uploadPartRequest(uploadPartRequest))
                    .url()
                    .toString();
            parts.add(new MultipartUpload.Part(partNumber, url));
        }
        return new MultipartUpload(uploadId, parts);
    }
}
