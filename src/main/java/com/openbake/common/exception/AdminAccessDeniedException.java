package com.openbake.common.exception;

public class AdminAccessDeniedException extends BusinessException {
    public AdminAccessDeniedException() {
        super(ErrorCode.ADMIN_ACCESS_DENIED);
    }

    public AdminAccessDeniedException(String message) {
        super(ErrorCode.ADMIN_ACCESS_DENIED, message);
    }
}
