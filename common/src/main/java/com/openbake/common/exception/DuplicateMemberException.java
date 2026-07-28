package com.openbake.common.exception;

public class DuplicateMemberException extends BusinessException {
    public DuplicateMemberException() {
        super(ErrorCode.DUPLICATE_RESOURCE);
    }

    public DuplicateMemberException(String message) {
        super(ErrorCode.DUPLICATE_RESOURCE, message);
    }
}
