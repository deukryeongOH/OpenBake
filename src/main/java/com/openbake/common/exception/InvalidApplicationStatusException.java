package com.openbake.common.exception;

public class InvalidApplicationStatusException extends BusinessException {
    public InvalidApplicationStatusException() {
        super(ErrorCode.INVALID_STATE);
    }

    public InvalidApplicationStatusException(String message) {
        super(ErrorCode.INVALID_STATE, message);
    }
}
