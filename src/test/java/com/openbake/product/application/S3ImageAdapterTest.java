package com.openbake.product.application;

import com.openbake.common.exception.BusinessException;
import com.openbake.common.exception.ErrorCode;
import com.openbake.product.application.dto.PresignedUploadResult;
import com.openbake.product.infrastructure.S3ImageAdapter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CopyObjectRequest;
import software.amazon.awssdk.services.s3.model.CopyObjectResponse;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.net.URI;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class S3ImageAdapterTest {

    private static final String BUCKET = "test-bucket";

    @Mock
    private S3Presigner s3Presigner;

    @Mock
    private S3Client s3Client;

    @Mock
    private PresignedPutObjectRequest presignedPutObjectRequest;

    private S3ImageAdapter s3ImageAdapter;

    @BeforeEach
    void setUp() {
        s3ImageAdapter = new S3ImageAdapter(s3Presigner, s3Client);
        ReflectionTestUtils.setField(s3ImageAdapter, "bucket", BUCKET);
    }

    @Test
    @DisplayName("업로드 URL을 발급하면 uploads/tmp/ 경로의 key와 presigned PUT URL이 함께 반환된다")
    @SuppressWarnings("unchecked")
    void issueUploadUrl_returnsPresignedUrlAndTmpKey() throws Exception {
        given(presignedPutObjectRequest.url()).willReturn(new URI("https://s3.example.com/presigned").toURL());
        ArgumentCaptor<Consumer<PutObjectPresignRequest.Builder>> captor = ArgumentCaptor.forClass(Consumer.class);
        given(s3Presigner.presignPutObject(captor.capture())).willReturn(presignedPutObjectRequest);

        PresignedUploadResult result = s3ImageAdapter.issueUploadUrl("image/png");

        assertThat(result.uploadUrl()).isEqualTo("https://s3.example.com/presigned");
        assertThat(result.key()).startsWith("uploads/tmp/");

        PutObjectPresignRequest.Builder builder = PutObjectPresignRequest.builder();
        captor.getValue().accept(builder);
        PutObjectPresignRequest presignRequest = builder.build();

        assertThat(presignRequest.putObjectRequest().bucket()).isEqualTo(BUCKET);
        assertThat(presignRequest.putObjectRequest().key()).isEqualTo(result.key());
        assertThat(presignRequest.putObjectRequest().contentType()).isEqualTo("image/png");
    }

    @Test
    @DisplayName("promote는 tmp 키를 products/{productId}/ 경로로 복사하고, 원본 tmp 객체는 삭제한 뒤 새 key를 반환한다")
    @SuppressWarnings("unchecked")
    void promote_copiesTmpObjectToProductPathAndDeletesTmpObject() {
        String tmpKey = "uploads/tmp/abc-123";
        Long productId = 42L;
        ArgumentCaptor<Consumer<CopyObjectRequest.Builder>> copyCaptor = ArgumentCaptor.forClass(Consumer.class);
        given(s3Client.copyObject(copyCaptor.capture())).willReturn(CopyObjectResponse.builder().build());
        ArgumentCaptor<Consumer<DeleteObjectRequest.Builder>> deleteCaptor = ArgumentCaptor.forClass(Consumer.class);
        given(s3Client.deleteObject(deleteCaptor.capture())).willReturn(DeleteObjectResponse.builder().build());

        String finalKey = s3ImageAdapter.promote(tmpKey, productId);

        assertThat(finalKey).isEqualTo("products/42/abc-123");

        CopyObjectRequest.Builder copyBuilder = CopyObjectRequest.builder();
        copyCaptor.getValue().accept(copyBuilder);
        CopyObjectRequest copyRequest = copyBuilder.build();

        assertThat(copyRequest.sourceBucket()).isEqualTo(BUCKET);
        assertThat(copyRequest.sourceKey()).isEqualTo(tmpKey);
        assertThat(copyRequest.destinationBucket()).isEqualTo(BUCKET);
        assertThat(copyRequest.destinationKey()).isEqualTo(finalKey);

        DeleteObjectRequest.Builder deleteBuilder = DeleteObjectRequest.builder();
        deleteCaptor.getValue().accept(deleteBuilder);
        DeleteObjectRequest deleteRequest = deleteBuilder.build();

        assertThat(deleteRequest.bucket()).isEqualTo(BUCKET);
        assertThat(deleteRequest.key()).isEqualTo(tmpKey);
    }

    @Test
    @DisplayName("tmp 키가 S3에 존재하지 않으면 IMAGE_NOT_FOUND(PR006) 예외를 던지고, tmp 객체 삭제는 시도하지 않는다")
    @SuppressWarnings("unchecked")
    void promote_throwsImageNotFound_whenTmpObjectDoesNotExist() {
        String tmpKey = "uploads/tmp/missing";
        Long productId = 42L;
        given(s3Client.copyObject(any(Consumer.class))).willThrow(NoSuchKeyException.builder().build());

        assertThatThrownBy(() -> s3ImageAdapter.promote(tmpKey, productId))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.IMAGE_NOT_FOUND);

        verify(s3Client, never()).deleteObject(any(Consumer.class));
    }
}