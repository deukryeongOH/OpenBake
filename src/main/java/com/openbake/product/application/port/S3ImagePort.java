package com.openbake.product.application.port;

import com.openbake.product.application.dto.PresignedUploadResult;

public interface S3ImagePort {
    PresignedUploadResult issueUploadUrl(String contentType);
    String promote(String tmpKey, Long productId);
}
