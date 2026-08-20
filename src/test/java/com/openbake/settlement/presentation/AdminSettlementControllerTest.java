package com.openbake.settlement.presentation;

import com.openbake.settlement.application.SettlementListResult;
import com.openbake.settlement.application.SettlementQueryService;
import com.openbake.settlement.application.SettlementResult;
import com.openbake.settlement.domain.SettlementStatus;
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
import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
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

    @Test
    @DisplayName("필터/페이지 조건으로 정산 목록을 조회한다")
    void getSettlements() throws Exception {
        SettlementResult item = new SettlementResult(
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

        SettlementListResult result = new SettlementListResult(
                List.of(item),
                0,
                20,
                false
        );

        when(settlementQueryService.search(
                eq(10L),
                isNull(),
                isNull(),
                eq(SettlementStatus.READY),
                eq(0),
                eq(20)
        )).thenReturn(result);

        mockMvc.perform(
                        get("/internal/v1/settlements")
                                .param("sellerId", "10")
                                .param("status", "READY")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content[0].settlementId").value(1))
                .andExpect(jsonPath("$.data.content[0].sellerId").value(10))
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.size").value(20))
                .andExpect(jsonPath("$.data.hasNext").value(false));
    }
}
