package com.openbake.common.exception;

public class AccountNotVerifiedException extends BusinessException {
    public AccountNotVerifiedException() {
        super(ErrorCode.ACCOUNT_NOT_VERIFIED);
    }

    public AccountNotVerifiedException(String message) {
        super(ErrorCode.ACCOUNT_NOT_VERIFIED, message);
    }
}
