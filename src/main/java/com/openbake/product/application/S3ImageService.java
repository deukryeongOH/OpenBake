package com.openbake.product.application;

import com.openbake.product.application.dto.PresignedUploadResult;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;

import java.time.Duration;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class S3ImageService {
    private final S3Presigner s3Presigner;
    private final S3Client s3Client;

    @Value("${cloud.aws.s3.bucket}")
    private String bucket;

    public PresignedUploadResult issueUploadUrl(String contentType){
        String key = "uploads/tmp/" + UUID.randomUUID();

        PutObjectRequest objectRequest = PutObjectRequest.builder()
                .bucket(bucket).key(key).contentType(contentType)
                .build();

        PresignedPutObjectRequest presignedPutObjectRequest = s3Presigner.presignPutObject(b -> b
                .signatureDuration(Duration.ofMinutes(3))
                .putObjectRequest(objectRequest));

        return PresignedUploadResult.create(presignedPutObjectRequest.url().toString(), key);
    }

    public String promote(String tmpKey, Long productId) {
        String finalKey = "products/" + productId + "/" + tmpKey.substring(tmpKey.lastIndexOf("/") + 1);
        s3Client.copyObject(b -> b.sourceBucket(bucket).sourceKey(tmpKey)
                .destinationBucket(bucket).destinationKey(finalKey));

        return finalKey;
    }
}
