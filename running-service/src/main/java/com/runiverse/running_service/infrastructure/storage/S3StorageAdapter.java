package com.runiverse.running_service.infrastructure.storage;

import com.runiverse.running_service.application.user.port.out.DeleteProfileImagesPort;
import com.runiverse.running_service.application.user.port.out.GenerateUploadUrlPort;
import com.runiverse.running_service.application.user.port.out.GenerateViewUrlPort;
import com.runiverse.running_service.application.user.port.out.LoadUploadedImagePort;
import com.runiverse.running_service.application.user.port.out.UploadedImage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.Delete;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.ObjectIdentifier;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.S3Object;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;

import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class S3StorageAdapter implements GenerateUploadUrlPort, LoadUploadedImagePort, GenerateViewUrlPort,
        DeleteProfileImagesPort {

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

    // S3에는 프리픽스 삭제 API가 없다 — 목록을 훑어 페이지마다 일괄 삭제한다.
    // 조회·삭제 모두 한 번에 1,000개까지 다루므로 사진이 몇 장이든 보통 각 1회다
    @Override
    public void deleteAllByPrefix(String keyPrefix) {
        s3Client.listObjectsV2Paginator(ListObjectsV2Request.builder()
                        .bucket(properties.userAssetBucket())
                        .prefix(keyPrefix)
                        .build())
                .forEach(page -> deleteObjects(page.contents()));
    }

    private void deleteObjects(List<S3Object> objects) {
        // 빈 목록을 넘기면 S3가 MalformedXML로 거절한다
        if (objects.isEmpty()) {
            return;
        }
        List<ObjectIdentifier> keys = objects.stream()
                .map(object -> ObjectIdentifier.builder().key(object.key()).build())
                .toList();
        s3Client.deleteObjects(request -> request
                .bucket(properties.userAssetBucket())
                .delete(Delete.builder().objects(keys).build()));
    }
}
