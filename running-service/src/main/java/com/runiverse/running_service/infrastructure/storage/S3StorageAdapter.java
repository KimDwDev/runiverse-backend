package com.runiverse.running_service.infrastructure.storage;

import com.runiverse.running_service.application.user.port.out.GenerateUploadUrlPort;
import com.runiverse.running_service.application.user.port.out.GenerateViewUrlPort;
import com.runiverse.running_service.application.user.port.out.LoadUploadedImagePort;
import com.runiverse.running_service.application.user.port.out.UploadedImage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class S3StorageAdapter implements GenerateUploadUrlPort, LoadUploadedImagePort, GenerateViewUrlPort {

    private final S3Presigner s3Presigner;
    private final S3Properties properties;
    private final S3Client s3Client;

    @Override
    public String generate(String key, String contentType, long sizeBytes) {
        // contentType을 서명에 포함해 클라가 다른 타입으로 올리지 못하게 막음
        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(properties.userAssetBucket())
                .key(key)
                .contentType(contentType)
                .contentLength(sizeBytes)
                .build();
        PresignedPutObjectRequest presignedRequest = s3Presigner.presignPutObject(builder -> builder
                .signatureDuration(properties.presignedUrlTtl())
                .putObjectRequest(putObjectRequest));
        return presignedRequest.url().toString();

    }

    @Override
    public Optional<UploadedImage> load(String key) {
        try {
            HeadObjectResponse head = s3Client.headObject(request -> request
                    .bucket(properties.userAssetBucket())
                    .key(key));
            return Optional.of(new UploadedImage(head.contentLength(), head.contentType()));
        } catch (NoSuchKeyException e) {
            return Optional.empty();
        } catch (S3Exception e) {
            if (e.statusCode() == 404) {
                return Optional.empty();
            }
            throw e;
        }
    }

    @Override
    public String generate(String key) {
        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(properties.userAssetBucket())
                .key(key)
                .build();
        PresignedGetObjectRequest presigned = s3Presigner.presignGetObject(builder -> builder
                .signatureDuration(properties.viewUrlTtl())
                .getObjectRequest(getObjectRequest));
        return presigned.url().toString();
    }
}
