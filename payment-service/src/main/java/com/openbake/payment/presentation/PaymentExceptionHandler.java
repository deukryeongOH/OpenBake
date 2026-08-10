package com.openbake.payment.presentation;

import com.openbake.common.exception.ErrorCode;
import com.openbake.common.response.ApiResponse;
import com.openbake.common.response.ApiResponse.ApiError;
import com.openbake.payment.application.port.PgApproveException;
import com.openbake.payment.application.port.PgUnknownResultException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class PaymentExceptionHandler {

    @ExceptionHandler(PgApproveException.class)
    public ResponseEntity<ApiResponse<Void>> handlePgApproveException(PgApproveException e) {
        ErrorCode errorCode = ErrorCode.PG_APPROVE_FAILED;
        return ResponseEntity.status(errorCode.getStatus())
                .body(ApiResponse.fail(new ApiError(errorCode.getCode(), errorCode.getMessage())));
    }

    @ExceptionHandler(PgUnknownResultException.class)
    public ResponseEntity<ApiResponse<Void>> handlePgUnknownResultException(PgUnknownResultException e) {
        ErrorCode errorCode = ErrorCode.PG_TIMEOUT;
        return ResponseEntity.status(errorCode.getStatus())
                .body(ApiResponse.fail(new ApiError(errorCode.getCode(), errorCode.getMessage())));
    }
}
