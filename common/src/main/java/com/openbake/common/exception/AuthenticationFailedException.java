package com.openbake.common.exception;

public class AuthenticationFailedException extends BusinessException {
    public AuthenticationFailedException() {
        super(ErrorCode.AUTHENTICATION_FAILED);
    }

    public AuthenticationFailedException(String message) {
        super(ErrorCode.AUTHENTICATION_FAILED, message);
    }
}
