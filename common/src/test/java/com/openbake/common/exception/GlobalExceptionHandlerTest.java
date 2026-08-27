package com.openbake.common.exception;

import com.openbake.common.response.ApiResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DB 무결성 제약 충돌·낙관적 락 충돌은 어느 도메인 테이블에서든 올 수 있는데, 예전에는
 * order 도메인의 DUPLICATE_REQUEST(OR006)를 그대로 반환해서 무관한 도메인(예: product)의
 * 충돌에도 "중복된 요청입니다"라는 order 전용 메시지가 나갔다. 공용 코드(RESOURCE_CONFLICT)로
 * 통일했는지 확인한다.
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    @DisplayName("DB 무결성 제약 충돌은 도메인 무관 공용 코드(RESOURCE_CONFLICT)로 응답한다 - order 전용 코드가 아니다")
    void dataIntegrityViolation_RespondsWithDomainNeutralConflictCode() {
        DataIntegrityViolationException exception = new DataIntegrityViolationException("constraint violated");

        ResponseEntity<ApiResponse<Void>> response = handler.handleDataIntegrityViolation(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().error().code()).isEqualTo(ErrorCode.RESOURCE_CONFLICT.getCode());
        assertThat(response.getBody().error().code()).isNotEqualTo(ErrorCode.DUPLICATE_REQUEST.getCode());
    }

    @Test
    @DisplayName("낙관적 락 충돌도 같은 공용 코드로 응답한다")
    void optimisticLockingFailure_RespondsWithSameConflictCode() {
        OptimisticLockingFailureException exception = new OptimisticLockingFailureException("version mismatch");

        ResponseEntity<ApiResponse<Void>> response = handler.handleOptimisticLock(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().error().code()).isEqualTo(ErrorCode.RESOURCE_CONFLICT.getCode());
    }
}
