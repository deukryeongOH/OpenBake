package com.openbake.payment.infrastructure.pg;

import com.openbake.payment.application.port.PgApproveException;
import com.openbake.payment.application.port.PgApproveResponse;
import com.openbake.payment.application.port.PgClient;
import com.openbake.payment.application.port.PgPaymentStatus;
import com.openbake.payment.application.port.PgUnknownResultException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Set;

/**
 * 토스페이먼츠 API 클라이언트.
 * RestClient(Spring 6.1+)를 사용한다. (ARD-003: webmvc 환경이므로 WebClient 대신 RestClient)
 *
 * 토스페이먼츠 승인 API 흐름:
 * 1. 프론트에서 결제창 완료 → paymentKey, orderId, amount를 우리 서버에 전달
 * 2. 우리 서버가 이 값으로 토스 승인 API(POST /confirm)를 호출
 * 3. 토스가 승인하면 결제 완료 응답 반환
 *
 * 인증 및 타임아웃 설정은 TossRestClientConfig에서 관리한다.
 */
@Slf4j
@Component
public class TossPaymentsClient implements PgClient {

    private final RestClient restClient;

    /**
     * 확정 실패 코드 화이트리스트.
     * 여기 있는 코드만 FAILED 처리. 나머지는 전부 "결과 모름"으로 간주한다.
     *
     * 기준: 승인 자체가 일어나지 않았다고 단정할 수 있는 것만.
     * - 카드 거절/한도 초과: 카드사가 거절한 것이므로 확정
     * - 인증 오류(401/403): 승인 요청이 인증 단계에서 막힘. 모름으로 두면 배치 조회도 같은 오류로 영구 IN_PROGRESS
     * - 결제 정보 없음(404): paymentKey가 토스에 없음. 배치 조회도 404로 영구 IN_PROGRESS
     */
    private static final Set<String> DEFINITE_FAILURE_CODES = Set.of(
            // 카드 거절
            "REJECT_CARD_PAYMENT",
            "REJECT_CARD_COMPANY",
            "REJECT_ACCOUNT_PAYMENT",
            // 카드 문제
            "INVALID_REJECT_CARD",
            "INVALID_STOPPED_CARD",
            "INVALID_CARD_LOST_OR_STOLEN",
            "INVALID_CARD_NUMBER",
            "INVALID_CARD_EXPIRATION",
            // 한도 초과
            "EXCEED_MAX_AMOUNT",
            "EXCEED_MAX_PAYMENT_AMOUNT",
            "EXCEED_MAX_DAILY_PAYMENT_COUNT",
            "EXCEED_MAX_MONTHLY_PAYMENT_AMOUNT",
            "EXCEED_MAX_ONE_DAY_AMOUNT",
            "EXCEED_MAX_AUTH_COUNT",
            "EXCEED_MAX_ONE_DAY_WITHDRAW_AMOUNT",
            "EXCEED_MAX_ONE_TIME_WITHDRAW_AMOUNT",
            // 기타 거절
            "FDS_ERROR",
            "INVALID_PASSWORD",
            "NOT_AVAILABLE_BANK",
            "NOT_AVAILABLE_PAYMENT",
            "BELOW_MINIMUM_AMOUNT",
            "NOT_FOUND_PAYMENT_SESSION",
            // 결제 정보 없음 — 배치 조회도 404
            "NOT_FOUND_PAYMENT",
            // 인증 오류 — 승인 요청이 인증 단계에서 막힘
            "UNAUTHORIZED_KEY",
            "INVALID_API_KEY",
            "INCORRECT_BASIC_AUTH_FORMAT",
            "FORBIDDEN_REQUEST"
    );

    private static final Set<String> AUTH_ERROR_CODES = Set.of(
            "UNAUTHORIZED_KEY",
            "INVALID_API_KEY",
            "INCORRECT_BASIC_AUTH_FORMAT",
            "FORBIDDEN_REQUEST"
    );

    public TossPaymentsClient(RestClient tossRestClient) {
        this.restClient = tossRestClient;
    }

    @Override
    public PgApproveResponse approve(String pgPaymentKey, String pgOrderId, BigDecimal amount) {
        PgApproveResponse response;

        try {
            response = restClient.post()
                    .uri("/confirm")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "paymentKey", pgPaymentKey,
                            "orderId", pgOrderId,
                            "amount", amount
                    ))
                    .retrieve()
                    .body(PgApproveResponse.class);
        } catch (HttpClientErrorException e) {
            // 4xx — 에러 코드 파싱해서 확정 실패 vs 모름 분류
            throw handleClientError(e);
        } catch (HttpServerErrorException e) {
            // 5xx — 결과 모름
            log.warn("[PG] 토스 서버 오류 — status={}, body={}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new PgUnknownResultException("PG 서버 오류: " + e.getStatusCode(), e);
        } catch (ResourceAccessException e) {
            // 타임아웃/네트워크 — 결과 모름
            log.warn("[PG] 네트워크 오류 — {}", e.getMessage());
            throw new PgUnknownResultException("네트워크 오류: " + e.getMessage(), e);
        } catch (Exception e) {
            // 예상 못 한 예외 — 안전하게 모름 처리
            log.warn("[PG] 예상치 못한 오류 — {}", e.getMessage(), e);
            throw new PgUnknownResultException("알 수 없는 오류: " + e.getMessage(), e);
        }

        // 응답 바디가 null이면 모름
        if (response == null) {
            log.warn("[PG] 승인 응답 바디가 null");
            throw new PgUnknownResultException("승인 응답 바디 없음");
        }

        // 200이지만 status가 DONE이 아니면 모름 (가상계좌 WAITING_FOR_DEPOSIT 등)
        if (!"DONE".equals(response.status())) {
            log.info("[PG] 승인 응답 status가 DONE이 아님 — status={}, paymentKey={}",
                    response.status(), response.paymentKey());
            throw new PgUnknownResultException("승인 미완료 상태: " + response.status());
        }

        return response;
    }

    /**
     * 4xx 에러 코드를 파싱해서 확정 실패 vs 모름을 분류한다.
     * 파싱 자체가 실패하면 안전하게 모름으로 처리한다.
     */
    private RuntimeException handleClientError(HttpClientErrorException e) {
        String code;
        String message;

        try {
            TossErrorResponse error = e.getResponseBodyAs(TossErrorResponse.class);
            code = (error != null && error.code() != null) ? error.code() : "UNKNOWN";
            message = (error != null && error.message() != null) ? error.message() : e.getMessage();
        } catch (Exception parseEx) {
            // 파싱 실패 — 모름으로 처리
            log.warn("[PG] 토스 에러 응답 파싱 실패 — status={}, body={}",
                    e.getStatusCode(), e.getResponseBodyAsString(), parseEx);
            return new PgUnknownResultException("에러 응답 파싱 실패: " + e.getStatusCode(), e);
        }

        // 인증 오류는 우리 설정 문제 — ERROR 레벨로 로그
        if (AUTH_ERROR_CODES.contains(code)) {
            log.error("[PG] 인증 오류 — code={}, message={}", code, message);
        }

        if (DEFINITE_FAILURE_CODES.contains(code)) {
            // 확정 실패
            log.info("[PG] 확정 실패 — code={}, message={}", code, message);
            return new PgApproveException(code, message);
        }

        // 화이트리스트에 없는 코드 — 모름으로 처리 + warn 로그
        log.warn("[PG] 미등록 에러 코드 — code={}, message={}, status={}", code, message, e.getStatusCode());
        return new PgUnknownResultException("미등록 에러 코드: " + code, e);
    }

    @Override
    public PgPaymentStatus getPaymentStatus(String pgPaymentKey) {
        return restClient.get()
                .uri("/{paymentKey}", pgPaymentKey)
                .retrieve()
                .body(PgPaymentStatus.class);
    }

    private record TossErrorResponse(String code, String message) {}
}
