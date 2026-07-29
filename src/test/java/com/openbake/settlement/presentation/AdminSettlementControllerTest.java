package com.openbake.settlement.presentation;

import com.openbake.member.infrastructure.jwt.JwtAuthenticationFilter;
import com.openbake.member.infrastructure.jwt.JwtTokenProvider;
import com.openbake.settlement.application.SettlementQueryService;
import com.openbake.settlement.application.SettlementResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminSettlementController.class)
@AutoConfigureMockMvc(addFilters = false)
class AdminSettlementControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SettlementQueryService settlementQueryService;

    /*
     * 현재 프로젝트의 @WebMvcTest에서 JWT Filter가 발견되므로
     * 테스트 컨텍스트 생성에 필요한 Mock입니다.
     */
    @MockitoBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @Test
    @DisplayName("정산 단건 상세를 조회한다")
    void getSettlement() throws Exception {
        SettlementResult result = new SettlementResult(
                1L,
                10L,
                LocalDate.of(2026, 7, 1),
                LocalDate.of(2026, 8, 1),
                new BigDecimal("50000.00"),
                new BigDecimal("5000.00"),
                new BigDecimal("45000.00"),
                new BigDecimal("0.00"),
                new BigDecimal("45000.00"),
                2,
                "READY",
                OffsetDateTime.parse("2026-07-29T10:00:00+09:00"),
                null
        );

        when(settlementQueryService.getSettlement(1L))
                .thenReturn(result);

        mockMvc.perform(
                        get("/internal/v1/settlements/1")
                )
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.success")
                                .value(true)
                )
                .andExpect(
                        jsonPath("$.data.settlementId")
                                .value(1)
                )
                .andExpect(
                        jsonPath("$.data.sellerId")
                                .value(10)
                )
                .andExpect(
                        jsonPath("$.data.status")
                                .value("READY")
                )
                .andExpect(
                        jsonPath("$.data.payoutAmount")
                                .value(45000.00)
                );
    }
}
