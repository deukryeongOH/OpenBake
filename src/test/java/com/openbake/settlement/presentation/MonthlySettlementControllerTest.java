//package com.openbake.settlement.presentation;
//
//import com.openbake.settlement.application.MonthlySettlementResult;
//import com.openbake.settlement.application.MonthlySettlementService;
//import org.junit.jupiter.api.DisplayName;
//import org.junit.jupiter.api.Test;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
//import org.springframework.http.MediaType;
//import org.springframework.test.context.bean.override.mockito.MockitoBean;
//import org.springframework.test.web.servlet.MockMvc;
//import tools.jackson.databind.ObjectMapper;
//
//import java.math.BigDecimal;
//import java.time.LocalDate;
//
//import static org.mockito.Mockito.verify;
//import static org.mockito.Mockito.when;
//import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
//import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
//import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
//
//@WebMvcTest(MonthlySettlementController.class)
//class MonthlySettlementControllerTest {
//
//    @Autowired
//    private MockMvc mockMvc;
//
//    @Autowired
//    private ObjectMapper objectMapper;
//
//    @MockitoBean
//
//    @MockitoBean
//    private MonthlySettlementService monthlySettlementService;
//
//    @Test
//    @DisplayName("월 정산 실행 결과를 반환한다")
//    void settleMonthly() throws Exception {
//        // given
//        LocalDate periodStart =
//                LocalDate.of(2026, 7, 1);
//
//        LocalDate periodEnd =
//                LocalDate.of(2026, 8, 1);
//
//        MonthlySettlementRequest request =
//                new MonthlySettlementRequest(
//                        periodStart,
//                        periodEnd
//                );
//
//        MonthlySettlementResult result =
//                new MonthlySettlementResult(
//                        periodStart,
//                        periodEnd,
//                        2,
//                        3,
//                        new BigDecimal("54000.00")
//                );
//
//        when(monthlySettlementService.settle(
//                periodStart,
//                periodEnd
//        )).thenReturn(result);
//
//        // when & then
//        mockMvc.perform(
//                        post("/internal/v1/settlements/monthly")
//                                .contentType(MediaType.APPLICATION_JSON)
//                                .content(
//                                        objectMapper.writeValueAsString(
//                                                request
//                                        )
//                                )
//                )
//                .andExpect(status().isOk())
//                .andExpect(
//                        jsonPath("$.periodStart")
//                                .value("2026-07-01")
//                )
//                .andExpect(
//                        jsonPath("$.periodEnd")
//                                .value("2026-08-01")
//                )
//                .andExpect(
//                        jsonPath("$.settlementCount")
//                                .value(2)
//                )
//                .andExpect(
//                        jsonPath("$.targetCount")
//                                .value(3)
//                )
//                .andExpect(
//                        jsonPath("$.totalPayoutAmount")
//                                .value(54000.00)
//                );
//
//        verify(monthlySettlementService).settle(
//                periodStart,
//                periodEnd
//        );
//    }
//}
