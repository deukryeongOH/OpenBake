package com.openbake.common.exception;

public class AccountVerificationExpiredException extends BusinessException {
    public AccountVerificationExpiredException() {
        super(ErrorCode.ACCOUNT_VERIFICATION_EXPIRED);
    }

    public AccountVerificationExpiredException(String message) {
        super(ErrorCode.ACCOUNT_VERIFICATION_EXPIRED, message);
    }
}
