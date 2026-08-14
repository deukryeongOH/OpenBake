package com.openbake.order.presentation;

import com.openbake.order.application.OrderService;
import com.openbake.order.application.SellerOrderPageResult;
import com.openbake.order.application.SellerOrderSummaryResult;
import com.openbake.order.domain.OrderState;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SellerOrderController.class)
@AutoConfigureMockMvc(addFilters = false)
class SellerOrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OrderService orderService;

    @Test
    @DisplayName("판매자 본인 판매내역 목록을 조회한다")
    void getSellerOrders() throws Exception {
        SellerOrderSummaryResult summary = new SellerOrderSummaryResult(
                101L,
                7L,
                "시그니처 소금빵",
                "김구매",
                2,
                new BigDecimal("5000"),
                OrderState.PAID,
                LocalDate.of(2026, 7, 17),
                LocalDateTime.of(2026, 7, 16, 11, 0),
                null,
                null
        );

        SellerOrderPageResult result = new SellerOrderPageResult(
                List.of(summary),
                0,
                10,
                1,
                1
        );

        when(orderService.getSellerOrders(isNull(), eq(0), eq(10)))
                .thenReturn(result);

        mockMvc.perform(get("/api/v1/sellers/me/orders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content[0].orderId").value(101))
                .andExpect(jsonPath("$.data.content[0].dropId").value(7))
                .andExpect(jsonPath("$.data.content[0].buyerName").value("김구매"))
                .andExpect(jsonPath("$.data.content[0].orderState").value("PAID"))
                .andExpect(jsonPath("$.data.totalElements").value(1));
    }
}
