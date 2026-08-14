//package com.openbake.settlement.presentation;
//
//import com.openbake.settlement.application.SettlementPaymentResult;
//import com.openbake.settlement.application.SettlementPaymentService;
//import org.junit.jupiter.api.DisplayName;
//import org.junit.jupiter.api.Test;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
//import org.springframework.test.context.bean.override.mockito.MockitoBean;
//import org.springframework.test.web.servlet.MockMvc;
//
//import java.math.BigDecimal;
//import java.time.OffsetDateTime;
//
//import static org.mockito.Mockito.when;
//import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
//import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
//import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
//
//@WebMvcTest(AdminSettlementPaymentController.class)
//class AdminSettlementPaymentControllerTest {
//
//    @Autowired
//    private MockMvc mockMvc;
//
//    @MockitoBean
//    private SettlementPaymentService settlementPaymentService;
//
//    /*
//     * 현재 프로젝트의 @WebMvcTest에서 JWT Filter가 발견되므로
//     * 테스트 컨텍스트 생성에 필요한 Mock입니다.
//     */
//    @MockitoBean
//
//    @MockitoBean
//
//    @Test
//    @DisplayName("정산 지급 시작 결과를 반환한다")
//    void startPayment() throws Exception {
//        OffsetDateTime updatedAt =
//                OffsetDateTime.parse(
//                        "2026-07-23T13:00:00+09:00"
//                );
//
//        SettlementPaymentResult result =
//                new SettlementPaymentResult(
//                        1L,
//                        10L,
//                        new BigDecimal("45000.00"),
//                        "PAYING",
//                        updatedAt,
//                        null
//                );
//
//        when(settlementPaymentService.start(1L))
//                .thenReturn(result);
//
//        mockMvc.perform(
//                        post(
//                                "/internal/v1/settlements/"
//                                        + "1/payments/start"
//                        )
//                )
//                .andExpect(status().isOk())
//                .andExpect(
//                        jsonPath("$.success")
//                                .value(true)
//                )
//                .andExpect(
//                        jsonPath("$.data.settlementId")
//                                .value(1)
//                )
//                .andExpect(
//                        jsonPath("$.data.status")
//                                .value("PAYING")
//                )
//                .andExpect(
//                        jsonPath("$.data.payoutAmount")
//                                .value(45000.00)
//                );
//    }
//}
