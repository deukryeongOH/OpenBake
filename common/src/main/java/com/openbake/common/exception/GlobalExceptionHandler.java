package com.openbake.common.exception;

import com.openbake.common.response.ApiResponse;
import com.openbake.common.response.ApiResponse.ApiError;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException e) {
        ErrorCode errorCode = e.getErrorCode();
        return build(errorCode.getStatus(), errorCode.getCode(), e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldError().getDefaultMessage();
        return build(ErrorCode.INVALID_INPUT.getStatus(), ErrorCode.INVALID_INPUT.getCode(), message);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArgument(IllegalArgumentException e) {
        return build(ErrorCode.INVALID_INPUT.getStatus(), ErrorCode.INVALID_INPUT.getCode(), e.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalState(IllegalStateException e) {
        return build(ErrorCode.INVALID_STATE.getStatus(), ErrorCode.INVALID_STATE.getCode(), e.getMessage());
    }

    @ExceptionHandler({
            HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class,
            HandlerMethodValidationException.class,
            MissingServletRequestParameterException.class})
    public ResponseEntity<ApiResponse<Void>> handleBadRequest(Exception e) {
        return build(ErrorCode.INVALID_INPUT.getStatus(), ErrorCode.INVALID_INPUT.getCode(), "요청 형식이 올바르지 않습니다.");
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoResourceFound(NoResourceFoundException e) {
        ErrorCode errorCode = ErrorCode.ENTITY_NOT_FOUND;
        return build(errorCode.getStatus(), errorCode.getCode(), errorCode.getMessage());
    }

    /**
     * 유니크 제약 위반 등 DB 무결성 제약 충돌.
     *
     * 같은 사용자의 동시 요청이 같은 행을 만들려 할 때 발생한다(예: confirm-entry 중복 클릭 →
     * drop_entries 의 uk_drop_member). 서버 결함이 아니라 요청 간 경합이므로 500이 아니라 409로 응답한다.
     *
     * 공용 코드(RESOURCE_CONFLICT)를 쓴다 — 이 예외는 어느 도메인 테이블에서든 올 수 있는데,
     * 예전에는 여기서 order 도메인의 DUPLICATE_REQUEST(OR006)를 그대로 반환해서 product 같은
     * 무관한 도메인의 충돌에도 "중복된 요청입니다"라는 order 전용 메시지가 나갔다. 원인을
     * 특정할 수 있는 경우(예: OrderRepositoryAdapter의 활성 슬롯 충돌 판별)는 그 도메인
     * 코드를 우선 쓰고, 여기는 특정하지 못한 나머지의 최후 폴백이다.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleDataIntegrityViolation(DataIntegrityViolationException e) {
        log.warn("데이터 무결성 제약 충돌", e);
        ErrorCode errorCode = ErrorCode.RESOURCE_CONFLICT;
        return build(errorCode.getStatus(), errorCode.getCode(), errorCode.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(Exception e) {
        log.error("처리되지 않은 예외 발생", e);
        return build(ErrorCode.INTERNAL_ERROR.getStatus(), ErrorCode.INTERNAL_ERROR.getCode(), ErrorCode.INTERNAL_ERROR.getMessage());
    }

    // 낙관적 락 충돌도 같은 성격(동시 요청 경합)이라 위와 같은 공용 코드를 쓴다.
    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ApiResponse<Void>> handleOptimisticLock(OptimisticLockingFailureException exception) {
        ErrorCode errorCode = ErrorCode.RESOURCE_CONFLICT;
        return build(errorCode.getStatus(), errorCode.getCode(), errorCode.getMessage());
    }

    private ResponseEntity<ApiResponse<Void>> build(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(ApiResponse.fail(new ApiError(code, message)));
    }
}
