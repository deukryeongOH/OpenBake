package com.openbake.common.exception;

public class InvalidIdTokenException extends BusinessException {
    public InvalidIdTokenException() {
        super(ErrorCode.INVALID_TOKEN);
    }

    public InvalidIdTokenException(String message) {
        super(ErrorCode.INVALID_TOKEN, message);
    }
}
