package com.openbake.common.exception;

public class AccountVerificationFailedException extends BusinessException {
    public AccountVerificationFailedException() {
        super(ErrorCode.ACCOUNT_VERIFICATION_FAILED);
    }

    public AccountVerificationFailedException(String message) {
        super(ErrorCode.ACCOUNT_VERIFICATION_FAILED, message);
    }
}
