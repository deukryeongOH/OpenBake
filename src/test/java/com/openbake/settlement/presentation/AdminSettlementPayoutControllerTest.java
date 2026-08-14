//package com.openbake.settlement.presentation;
//
//import com.openbake.common.exception.GlobalExceptionHandler;
//import com.openbake.settlement.application.SettlementPayoutQueryService;
//import com.openbake.settlement.application.SettlementPayoutResult;
//import com.openbake.settlement.application.SettlementPayoutService;
//import org.junit.jupiter.api.DisplayName;
//import org.junit.jupiter.api.Test;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
//import org.springframework.context.annotation.Import;
//import org.springframework.http.MediaType;
//import org.springframework.test.context.bean.override.mockito.MockitoBean;
//import org.springframework.test.web.servlet.MockMvc;
//
//import java.math.BigDecimal;
//import java.time.OffsetDateTime;
//import java.util.List;
//
//import static org.mockito.Mockito.verifyNoInteractions;
//import static org.mockito.Mockito.when;
//import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
//import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
//import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
//import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
//
//@WebMvcTest(AdminSettlementPayoutController.class)
//@Import(GlobalExceptionHandler.class)
//class AdminSettlementPayoutControllerTest {
//
//    @Autowired
//    private MockMvc mockMvc;
//
//    @MockitoBean
//    private SettlementPayoutService payoutService;
//
//    @MockitoBean
//    private SettlementPayoutQueryService payoutQueryService;
//
//    /*
//     * 현재 프로젝트의 MVC Slice에서 JWT Filter가 발견되므로
//     * 실제 필터 생성 대신 Mock Bean을 제공합니다.
//     */
//    @MockitoBean
//
//    @MockitoBean
//
//    @Test
//    @DisplayName("정산 지급을 시작한다")
//    void start() throws Exception {
//        OffsetDateTime requestedAt =
//                OffsetDateTime.parse(
//                        "2026-07-23T13:00:00+09:00"
//                );
//
//        SettlementPayoutResult result =
//                new SettlementPayoutResult(
//                        100L,
//                        1L,
//                        10L,
//                        new BigDecimal("45000.00"),
//                        "settlement-1-payout-1",
//                        "PROCESSING",
//                        null,
//                        null,
//                        requestedAt,
//                        null,
//                        null
//                );
//
//        when(payoutService.start(
//                1L,
//                "settlement-1-payout-1"
//        )).thenReturn(result);
//
//        String requestBody = """
//                {
//                  "idempotencyKey": "settlement-1-payout-1"
//                }
//                """;
//
//        mockMvc.perform(
//                        post(
//                                "/internal/v1/settlements/"
//                                        + "1/payouts"
//                        )
//                                .contentType(
//                                        MediaType.APPLICATION_JSON
//                                )
//                                .content(requestBody)
//                )
//                .andExpect(status().isOk())
//                .andExpect(
//                        jsonPath("$.success")
//                                .value(true)
//                )
//                .andExpect(
//                        jsonPath("$.data.payoutId")
//                                .value(100)
//                )
//                .andExpect(
//                        jsonPath("$.data.settlementId")
//                                .value(1)
//                )
//                .andExpect(
//                        jsonPath("$.data.status")
//                                .value("PROCESSING")
//                );
//    }
//
//    @Test
//    @DisplayName("멱등키가 누락되면 400 응답을 반환한다")
//    void rejectMissingIdempotencyKey() throws Exception {
//        mockMvc.perform(
//                        post(
//                                "/internal/v1/settlements/"
//                                        + "1/payouts"
//                        )
//                                .contentType(
//                                        MediaType.APPLICATION_JSON
//                                )
//                                .content("{}")
//                )
//                .andExpect(status().isBadRequest())
//                .andExpect(
//                        jsonPath("$.success")
//                                .value(false)
//                )
//                .andExpect(
//                        jsonPath("$.error.code")
//                                .value("C001")
//                )
//                .andExpect(
//                        jsonPath("$.error.message")
//                                .value("멱등키는 필수입니다.")
//                );
//
//        verifyNoInteractions(payoutService);
//    }
//
//    @Test
//    @DisplayName("외부 거래 ID가 누락되면 400 응답을 반환한다")
//    void rejectMissingExternalTransactionId()
//            throws Exception {
//
//        mockMvc.perform(
//                        post(
//                                "/internal/v1/"
//                                        + "settlement-payouts/"
//                                        + "100/complete"
//                        )
//                                .contentType(
//                                        MediaType.APPLICATION_JSON
//                                )
//                                .content("{}")
//                )
//                .andExpect(status().isBadRequest())
//                .andExpect(
//                        jsonPath("$.error.code")
//                                .value("C001")
//                )
//                .andExpect(
//                        jsonPath("$.error.message")
//                                .value("외부 거래 ID는 필수입니다.")
//                );
//
//        verifyNoInteractions(payoutService);
//    }
//
//    @Test
//    @DisplayName("지급 실패 사유가 누락되면 400 응답을 반환한다")
//    void rejectMissingFailureReason()
//            throws Exception {
//
//        mockMvc.perform(
//                        post(
//                                "/internal/v1/"
//                                        + "settlement-payouts/"
//                                        + "100/fail"
//                        )
//                                .contentType(
//                                        MediaType.APPLICATION_JSON
//                                )
//                                .content("{}")
//                )
//                .andExpect(status().isBadRequest())
//                .andExpect(
//                        jsonPath("$.error.code")
//                                .value("C001")
//                )
//                .andExpect(
//                        jsonPath("$.error.message")
//                                .value("지급 실패 사유는 필수입니다.")
//                );
//
//        verifyNoInteractions(payoutService);
//    }
//
//    /** 단건 조회 테스트 */
//    @Test
//    @DisplayName("지급 이력을 단건 조회한다")
//    void getPayout() throws Exception {
//        OffsetDateTime requestedAt =
//                OffsetDateTime.parse(
//                        "2026-07-23T13:00:00+09:00"
//                );
//
//        SettlementPayoutResult result =
//                new SettlementPayoutResult(
//                        100L,
//                        1L,
//                        10L,
//                        new BigDecimal("45000.00"),
//                        "settlement-1-payout-1",
//                        "PROCESSING",
//                        null,
//                        null,
//                        requestedAt,
//                        null,
//                        null
//                );
//
//        when(payoutQueryService.getPayout(100L))
//                .thenReturn(result);
//
//        mockMvc.perform(
//                        get(
//                                "/internal/v1/"
//                                        + "settlement-payouts/100"
//                        )
//                )
//                .andExpect(status().isOk())
//                .andExpect(
//                        jsonPath("$.success")
//                                .value(true)
//                )
//                .andExpect(
//                        jsonPath("$.data.payoutId")
//                                .value(100)
//                )
//                .andExpect(
//                        jsonPath("$.data.status")
//                                .value("PROCESSING")
//                );
//    }
//
//    /** 목록 조회 테스트 */
//    @Test
//    @DisplayName("정산별 지급 이력 목록을 조회한다")
//    void getPayouts() throws Exception {
//        SettlementPayoutResult first =
//                new SettlementPayoutResult(
//                        101L,
//                        1L,
//                        10L,
//                        new BigDecimal("45000.00"),
//                        "settlement-1-payout-2",
//                        "FAILED",
//                        null,
//                        "계좌 정보 오류",
//                        OffsetDateTime.parse(
//                                "2026-07-23T14:00:00+09:00"
//                        ),
//                        null,
//                        OffsetDateTime.parse(
//                                "2026-07-23T14:01:00+09:00"
//                        )
//                );
//
//        SettlementPayoutResult second =
//                new SettlementPayoutResult(
//                        100L,
//                        1L,
//                        10L,
//                        new BigDecimal("45000.00"),
//                        "settlement-1-payout-1",
//                        "FAILED",
//                        null,
//                        "일시적인 은행 오류",
//                        OffsetDateTime.parse(
//                                "2026-07-23T13:00:00+09:00"
//                        ),
//                        null,
//                        OffsetDateTime.parse(
//                                "2026-07-23T13:01:00+09:00"
//                        )
//                );
//
//        when(payoutQueryService.getPayouts(1L))
//                .thenReturn(List.of(first, second));
//
//        mockMvc.perform(
//                        get(
//                                "/internal/v1/"
//                                        + "settlements/1/payouts"
//                        )
//                )
//                .andExpect(status().isOk())
//                .andExpect(
//                        jsonPath("$.data.payouts.length()")
//                                .value(2)
//                )
//                .andExpect(
//                        jsonPath("$.data.payouts[0].payoutId")
//                                .value(101)
//                );
//    }
//}
