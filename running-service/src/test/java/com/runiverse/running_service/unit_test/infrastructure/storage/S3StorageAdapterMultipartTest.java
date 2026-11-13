package com.runiverse.running_service.unit_test.infrastructure.storage;

import com.runiverse.running_service.application.recordcard.port.out.MultipartUpload;
import com.runiverse.running_service.infrastructure.storage.S3Properties;
import com.runiverse.running_service.infrastructure.storage.S3StorageAdapter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.CreateMultipartUploadResponse;
import software.amazon.awssdk.services.s3.model.UploadPartRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.UploadPartPresignRequest;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

// 서명은 실제 presigner로 만들고(네트워크 없음), 서명에 넣은 요청은 가로채 크기를 본다 — URL에는 서명한 헤더 이름만 보인다
@SuppressWarnings("unchecked")
@DisplayName("S3 나눠 올리기 시작 어댑터 단위 테스트")
class S3StorageAdapterMultipartTest {

    private static final String REGION = "ap-northeast-2";
    private static final String USER_ASSET_BUCKET = "runiverse-user-assets-test";
    private static final String GPS_TRACK_BUCKET = "runiverse-gps-tracks-test";
    private static final String KEY = "record-cards/u/c/i/image.jpg";
    private static final long PART_SIZE = 10L * 1024 * 1024;
    private static final Duration URL_TTL = Duration.ofMinutes(30);

    private S3Presigner realPresigner;
    private S3Client s3Client;
    private S3StorageAdapter adapter;
    private final List<UploadPartRequest> signedParts = new ArrayList<>();
    private CreateMultipartUploadRequest createRequest;
    private Duration signedTtl;

    @BeforeEach
    void setUp() {
        realPresigner = S3Presigner.builder()
                .region(Region.of(REGION))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create("AKIATESTTESTTESTTEST", "test-secret-key")))
                .build();
        S3Presigner presigner = mock(S3Presigner.class);
        when(presigner.presignUploadPart(any(Consumer.class))).thenAnswer(invocation -> {
            UploadPartPresignRequest.Builder builder = UploadPartPresignRequest.builder();
            ((Consumer<UploadPartPresignRequest.Builder>) invocation.getArgument(0)).accept(builder);
            UploadPartPresignRequest request = builder.build();
            signedParts.add(request.uploadPartRequest());
            signedTtl = request.signatureDuration();
            return realPresigner.presignUploadPart(request);
        });

        s3Client = mock(S3Client.class);
        when(s3Client.createMultipartUpload(any(Consumer.class))).thenAnswer(invocation -> {
            CreateMultipartUploadRequest.Builder builder = CreateMultipartUploadRequest.builder();
            ((Consumer<CreateMultipartUploadRequest.Builder>) invocation.getArgument(0)).accept(builder);
            createRequest = builder.build();
            return CreateMultipartUploadResponse.builder().uploadId("s3-upload-id").build();
        });

        adapter = new S3StorageAdapter(presigner, new S3Properties(REGION, USER_ASSET_BUCKET, GPS_TRACK_BUCKET,
                Duration.ofMinutes(10), Duration.ofHours(1), null, null), s3Client);
    }

    @AfterEach
    void tearDown() {
        realPresigner.close();
    }

    @Test
    @DisplayName("사용자 사진 버킷에 형식을 넣어 시작하고, S3가 준 UploadId를 돌려준다")
    void startWithContentType() {
        // when
        MultipartUpload upload = adapter.start(KEY, "image/png", PART_SIZE + 1, PART_SIZE, URL_TTL);

        // then -> 조각에는 Content-Type이 없어 합친 사진의 형식은 시작 요청이 정한다
        assertThat(upload.uploadId()).isEqualTo("s3-upload-id");
        assertThat(createRequest.bucket()).isEqualTo(USER_ASSET_BUCKET);
        assertThat(createRequest.key()).isEqualTo(KEY);
        assertThat(createRequest.contentType()).isEqualTo("image/png");
    }

    @Test
    @DisplayName("조각 수는 크기를 조각 크기로 나눠 올림하고, 마지막 조각만 작게 서명한다")
    void splitIntoParts() {
        // given
        long size = PART_SIZE * 2 + 123;

        // when
        MultipartUpload upload = adapter.start(KEY, "image/jpeg", size, PART_SIZE, URL_TTL);

        // then
        assertThat(upload.parts()).extracting(MultipartUpload.Part::partNumber).containsExactly(1, 2, 3);
        assertThat(signedParts).extracting(UploadPartRequest::contentLength)
                .containsExactly(PART_SIZE, PART_SIZE, 123L);
        assertThat(signedParts).allSatisfy(part -> {
            assertThat(part.bucket()).isEqualTo(USER_ASSET_BUCKET);
            assertThat(part.key()).isEqualTo(KEY);
            assertThat(part.uploadId()).isEqualTo("s3-upload-id");
        });
    }

    @Test
    @DisplayName("크기가 조각 크기로 딱 나눠지면 마지막 조각도 같은 크기다")
    void exactMultiple() {
        // when
        MultipartUpload upload = adapter.start(KEY, "image/jpeg", PART_SIZE * 2, PART_SIZE, URL_TTL);

        // then
        assertThat(upload.parts()).hasSize(2);
        assertThat(signedParts).extracting(UploadPartRequest::contentLength).containsExactly(PART_SIZE, PART_SIZE);
    }

    @Test
    @DisplayName("조각 URL은 조각 번호·UploadId를 담고, 크기를 서명하며, 받은 유효 시간을 따른다")
    void partUrlsAreSigned() {
        // when
        MultipartUpload upload = adapter.start(KEY, "image/jpeg", PART_SIZE + 1, PART_SIZE, URL_TTL);

        // then -> 크기를 서명해야 앱이 다르게 자르면 S3가 403으로 막는다
        String second = upload.parts().get(1).uploadUrl();
        assertThat(second).startsWith("https://%s.s3.%s.amazonaws.com/%s?".formatted(USER_ASSET_BUCKET, REGION, KEY));
        assertThat(queryParam(second, "partNumber")).isEqualTo("2");
        assertThat(queryParam(second, "uploadId")).isEqualTo("s3-upload-id");
        assertThat(queryParam(second, "X-Amz-SignedHeaders")).contains("content-length");
        assertThat(queryParam(second, "X-Amz-Expires")).isEqualTo(String.valueOf(URL_TTL.toSeconds()));
        assertThat(signedTtl).isEqualTo(URL_TTL);
    }

    private static String queryParam(String url, String name) {
        String query = url.split("\\?", 2)[1];
        for (String pair : query.split("&")) {
            String[] keyValue = pair.split("=", 2);
            if (keyValue[0].equals(name)) {
                return URLDecoder.decode(keyValue[1], StandardCharsets.UTF_8);
            }
        }
        throw new AssertionError("query param not found: " + name);
    }
}
