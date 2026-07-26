package com.openbake.common.exception;

public class InvalidSettlementAccountException extends BusinessException {
    public InvalidSettlementAccountException() {
        super(ErrorCode.INVALID_SETTLEMENT_ACCOUNT);
    }

    public InvalidSettlementAccountException(String message) {
        super(ErrorCode.INVALID_SETTLEMENT_ACCOUNT, message);
    }
}
