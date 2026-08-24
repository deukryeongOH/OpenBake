package com.openbake.ai.presentation;

import com.openbake.ai.application.RecommendationUnavailableException;
import com.openbake.ai.application.SemanticSearchUnavailableException;
import com.openbake.common.response.ApiResponse;
import com.openbake.common.response.ApiResponse.ApiError;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@Slf4j
@RestControllerAdvice
public class RecommendationExceptionHandler {

    @ExceptionHandler(RecommendationUnavailableException.class)
    ResponseEntity<ApiResponse<Void>> unavailable(RecommendationUnavailableException exception) {
        log.warn("추천 제공 실패", exception);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ApiResponse.fail(new ApiError(
                        "AI_RECOMMENDATION_UNAVAILABLE",
                        "추천 서비스를 일시적으로 사용할 수 없습니다.")));
    }

    @ExceptionHandler(SemanticSearchUnavailableException.class)
    ResponseEntity<ApiResponse<Void>> semanticSearchUnavailable(SemanticSearchUnavailableException exception) {
        log.warn("의미 검색 실패", exception);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ApiResponse.fail(new ApiError(
                        "AI_SEMANTIC_SEARCH_UNAVAILABLE",
                        "의미 검색을 일시적으로 사용할 수 없습니다.")));
    }

    @ExceptionHandler({
            IllegalArgumentException.class,
            MethodArgumentNotValidException.class,
            MethodArgumentTypeMismatchException.class
    })
    ResponseEntity<ApiResponse<Void>> invalidInput(Exception exception) {
        log.debug("잘못된 추천 요청", exception);
        return ResponseEntity.badRequest()
                .body(ApiResponse.fail(new ApiError("C001", "잘못된 요청입니다.")));
    }
}
