package com.runiverse.running_service.infrastructure.storage;

import com.runiverse.running_service.application.user.port.out.GenerateUploadUrlPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;

@Component
@RequiredArgsConstructor
public class S3StorageAdapter implements GenerateUploadUrlPort {

    private final S3Presigner s3Presigner;
    private final S3Properties properties;

    @Override
    public String generate(String key, String contentType) {
        // contentType을 서명에 포함해 클라가 다른 타입으로 올리지 못하게 막음
        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(properties.bucket())
                .key(key)
                .contentType(contentType)
                .build();
        PresignedPutObjectRequest presignedRequest = s3Presigner.presignPutObject(builder -> builder
                .signatureDuration(properties.presignedUrlTtl())
                .putObjectRequest(putObjectRequest));
        return presignedRequest.url().toString();

    }
}
