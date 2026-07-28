package com.openbake.common.exception;

public class BusinessVerificationFailedException extends BusinessException {
    public BusinessVerificationFailedException() {
        super(ErrorCode.BUSINESS_VERIFICATION_FAILED);
    }

    public BusinessVerificationFailedException(String message) {
        super(ErrorCode.BUSINESS_VERIFICATION_FAILED, message);
    }
}
