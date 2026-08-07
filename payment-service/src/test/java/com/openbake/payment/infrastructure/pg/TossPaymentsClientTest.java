package com.openbake.payment.infrastructure.pg;

import com.openbake.payment.application.port.PgApproveException;
import com.openbake.payment.application.port.PgApproveResponse;
import com.openbake.payment.application.port.PgUnknownResultException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * TossPaymentsClient 에러 분류 테스트.
 * MockRestServiceServer로 실제 RestClient 흐름을 타서
 * getResponseBodyAs()가 동작하는지, 에러 코드 분류가 정확한지 검증한다.
 */
class TossPaymentsClientTest {

    private TossPaymentsClient client;
    private MockRestServiceServer mockServer;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder()
                .baseUrl("https://api.tosspayments.com/v1/payments");

        // MockRestServiceServer가 builder의 requestFactory를 교체
        mockServer = MockRestServiceServer.bindTo(builder).build();

        // builder.build()로 만든 RestClient를 TossPaymentsClient에 직접 주입
        client = new TossPaymentsClient(builder.build());
    }

    @Test
    @DisplayName("200 + DONE → 정상 응답 반환")
    void approve_success() {
        mockServer.expect(requestTo("https://api.tosspayments.com/v1/payments/confirm"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("""
                        {"paymentKey": "pk_123", "orderId": "order_1", "method": "카드", "status": "DONE"}
                        """, MediaType.APPLICATION_JSON));

        PgApproveResponse response = client.approve("pk_123", "order_1", new BigDecimal("10000"));

        assertThat(response.status()).isEqualTo("DONE");
        assertThat(response.method()).isEqualTo("카드");
        mockServer.verify();
    }

    @Test
    @DisplayName("200 + WAITING_FOR_DEPOSIT → PgUnknownResultException (가상계좌)")
    void approve_waitingForDeposit() {
        mockServer.expect(requestTo("https://api.tosspayments.com/v1/payments/confirm"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("""
                        {"paymentKey": "pk_123", "orderId": "order_1", "method": "가상계좌", "status": "WAITING_FOR_DEPOSIT"}
                        """, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.approve("pk_123", "order_1", new BigDecimal("10000")))
                .isInstanceOf(PgUnknownResultException.class)
                .hasMessageContaining("승인 미완료 상태");
        mockServer.verify();
    }

    @Test
    @DisplayName("403 + REJECT_CARD_PAYMENT → PgApproveException (확정 실패, 4xx 바디 파싱 검증)")
    void approve_cardRejected() {
        mockServer.expect(requestTo("https://api.tosspayments.com/v1/payments/confirm"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.FORBIDDEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                {"code": "REJECT_CARD_PAYMENT", "message": "한도초과 또는 잔액부족으로 결제에 실패했습니다."}
                                """));

        assertThatThrownBy(() -> client.approve("pk_123", "order_1", new BigDecimal("10000")))
                .isInstanceOf(PgApproveException.class)
                .satisfies(ex -> {
                    PgApproveException pgEx = (PgApproveException) ex;
                    assertThat(pgEx.getFailureCode()).isEqualTo("REJECT_CARD_PAYMENT");
                    assertThat(pgEx.getFailureReason()).contains("한도초과");
                });
        mockServer.verify();
    }

    @Test
    @DisplayName("401 + UNAUTHORIZED_KEY → PgApproveException (인증 오류도 확정 실패)")
    void approve_unauthorized() {
        mockServer.expect(requestTo("https://api.tosspayments.com/v1/payments/confirm"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                {"code": "UNAUTHORIZED_KEY", "message": "인증되지 않은 시크릿 키 혹은 클라이언트 키입니다."}
                                """));

        assertThatThrownBy(() -> client.approve("pk_123", "order_1", new BigDecimal("10000")))
                .isInstanceOf(PgApproveException.class)
                .satisfies(ex -> {
                    PgApproveException pgEx = (PgApproveException) ex;
                    assertThat(pgEx.getFailureCode()).isEqualTo("UNAUTHORIZED_KEY");
                });
        mockServer.verify();
    }

    @Test
    @DisplayName("400 + ALREADY_PROCESSED_PAYMENT → PgUnknownResultException (모름)")
    void approve_alreadyProcessed() {
        mockServer.expect(requestTo("https://api.tosspayments.com/v1/payments/confirm"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                {"code": "ALREADY_PROCESSED_PAYMENT", "message": "이미 처리된 결제입니다."}
                                """));

        assertThatThrownBy(() -> client.approve("pk_123", "order_1", new BigDecimal("10000")))
                .isInstanceOf(PgUnknownResultException.class);
        mockServer.verify();
    }

    @Test
    @DisplayName("500 서버 오류 → PgUnknownResultException (모름)")
    void approve_serverError() {
        mockServer.expect(requestTo("https://api.tosspayments.com/v1/payments/confirm"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                {"code": "UNKNOWN_PAYMENT_ERROR", "message": "알 수 없는 오류"}
                                """));

        assertThatThrownBy(() -> client.approve("pk_123", "order_1", new BigDecimal("10000")))
                .isInstanceOf(PgUnknownResultException.class);
        mockServer.verify();
    }

    @Test
    @DisplayName("미등록 에러 코드 → PgUnknownResultException (모름)")
    void approve_unknownErrorCode() {
        mockServer.expect(requestTo("https://api.tosspayments.com/v1/payments/confirm"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                {"code": "NEW_TOSS_ERROR_CODE", "message": "토스가 새로 추가한 에러"}
                                """));

        assertThatThrownBy(() -> client.approve("pk_123", "order_1", new BigDecimal("10000")))
                .isInstanceOf(PgUnknownResultException.class);
        mockServer.verify();
    }
}
