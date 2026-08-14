package com.runiverse.running_service.unit_test.infrastructure.storage;

import com.runiverse.running_service.infrastructure.storage.S3Properties;
import com.runiverse.running_service.infrastructure.storage.S3StorageAdapter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.time.Duration;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings("unchecked")
@ExtendWith(MockitoExtension.class)
@DisplayName("S3 업로드 URL 어댑터 단위 테스트")
public class S3StorageAdapterTest {

    private static final String REGION = "ap-northeast-2";
    private static final String BUCKET = "runiverse-test-bucket";
    private static final Duration TTL = Duration.ofMinutes(10);
    private static final String KEY = "profiles/9f1cf1a0-0000-7000-8000-000000000001/0198a3f2-0000-7000-8000" +
            "-000000000002.jpg";
    private static final String CONTENT_TYPE = "image/jpeg";
    private static final long SIZE_BYTES = 20_480L;
    private static final String PRESIGNED_URL =
            "https://runiverse-test-bucket.s3.ap-northeast-2.amazonaws.com/" + KEY + "?X-Amz-Signature=test";

    @Mock
    private S3Presigner s3Presigner;

    @Mock
    private PresignedPutObjectRequest presignedPutObjectRequest;

    private S3StorageAdapter adapter;

    @BeforeEach
    void setUp() {
        // 서명은 네트워크 없이 로컬에서 끝나므로 자격증명은 형식만 맞추면 된다
        presigner = S3Presigner.builder()
                .region(Region.of(REGION))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create("AKIATESTTESTTESTTEST", "test-secret-key")))
                .build();
        adapter = new S3StorageAdapter(presigner, new S3Properties(REGION, BUCKET, TTL, null, null), null);
    }

    // 어댑터가 넘긴 람다를 실제 빌더에 적용해 무엇을 설정했는지 꺼낸다
    private PutObjectPresignRequest capturePresignRequest() {
        ArgumentCaptor<Consumer<PutObjectPresignRequest.Builder>> captor =
                ArgumentCaptor.forClass(Consumer.class);
        verify(s3Presigner).presignPutObject(captor.capture());
        PutObjectPresignRequest.Builder builder = PutObjectPresignRequest.builder();
        captor.getValue().accept(builder);
        return builder.build();
    }

    private static URL presignedUrl() {
        try {
            return new URI(PRESIGNED_URL).toURL();
        } catch (URISyntaxException | MalformedURLException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    @DisplayName("설정한 버킷과 요청한 key로 업로드 요청을 만든다")
    void buildsPutObjectRequestForRequestedKey() {
        // when
        adapter.generate(KEY, CONTENT_TYPE, SIZE_BYTES);

        // then
        PutObjectRequest putObjectRequest = capturePresignRequest().putObjectRequest();
        assertThat(putObjectRequest.bucket()).isEqualTo(BUCKET);
        assertThat(putObjectRequest.key()).isEqualTo(KEY);
    }

    @Test
    @DisplayName("형식과 크기를 요청에 담아 서명 대상으로 넘긴다")
    void passesContentTypeAndLengthToSigning() {
        // when
        adapter.generate(KEY, CONTENT_TYPE, SIZE_BYTES);

        // then -> 이 두 값이 서명에 들어가야 클라가 다른 타입·크기로 올리지 못한다
        PutObjectRequest putObjectRequest = capturePresignRequest().putObjectRequest();
        assertThat(putObjectRequest.contentType()).isEqualTo(CONTENT_TYPE);
        assertThat(putObjectRequest.contentLength()).isEqualTo(SIZE_BYTES);
    }

    @Test
    @DisplayName("만료 시간은 설정한 TTL을 따른다")
    void expirationFollowsConfiguredTtl() {
        // when
        adapter.generate(KEY, CONTENT_TYPE, SIZE_BYTES);

        // then
        assertThat(capturePresignRequest().signatureDuration()).isEqualTo(TTL);
    }

    @Test
    @DisplayName("발급받은 URL을 그대로 돌려준다")
    void returnsPresignedUrl() {
        // when
        String uploadUrl = adapter.generate(KEY, CONTENT_TYPE, SIZE_BYTES);

        // then
        assertThat(uploadUrl).isEqualTo(PRESIGNED_URL);
    }
}
