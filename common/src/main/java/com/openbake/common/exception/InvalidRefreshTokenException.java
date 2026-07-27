package com.openbake.common.exception;

public class InvalidRefreshTokenException extends BusinessException {
    public InvalidRefreshTokenException() {
        super(ErrorCode.INVALID_TOKEN);
    }

    public InvalidRefreshTokenException(String message) {
        super(ErrorCode.INVALID_TOKEN, message);
    }
}
