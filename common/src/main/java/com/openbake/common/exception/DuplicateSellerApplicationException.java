package com.openbake.common.exception;

public class DuplicateSellerApplicationException extends BusinessException {
    public DuplicateSellerApplicationException() {
        super(ErrorCode.DUPLICATE_SELLER_APPLICATION);
    }

    public DuplicateSellerApplicationException(String message) {
        super(ErrorCode.DUPLICATE_SELLER_APPLICATION, message);
    }
}
