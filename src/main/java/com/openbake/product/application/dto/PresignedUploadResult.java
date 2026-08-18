package com.openbake.product.application.dto;


public record PresignedUploadResult(String uploadUrl, String key) {
    public static PresignedUploadResult create(String uploadUrl, String key) {
        return new PresignedUploadResult(uploadUrl, key);
    }
}
