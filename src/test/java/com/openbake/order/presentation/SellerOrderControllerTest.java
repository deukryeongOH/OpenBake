package com.openbake.order.presentation;

import com.openbake.common.security.jwt.JwtAuthenticationFilter;
import com.openbake.common.security.jwt.JwtTokenProvider;
import com.openbake.order.application.OrderService;
import com.openbake.order.domain.OrderState;
import com.openbake.order.presentation.dto.SellerOrderPageResponse;
import com.openbake.order.presentation.dto.SellerOrderSummaryResponse;
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

    /*
     * 현재 프로젝트의 @WebMvcTest에서 JWT Filter가 발견되므로
     * 테스트 컨텍스트 생성에 필요한 Mock입니다.
     */
    @MockitoBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @Test
    @DisplayName("판매자 본인 판매내역 목록을 조회한다")
    void getSellerOrders() throws Exception {
        SellerOrderSummaryResponse summary = SellerOrderSummaryResponse.builder()
                .orderId(101L)
                .dropId(7L)
                .dropName("시그니처 소금빵")
                .buyerName("김구매")
                .quantity(2)
                .totalAmount(new BigDecimal("5000"))
                .orderState(OrderState.PAID)
                .pickupDate(LocalDate.of(2026, 7, 17))
                .paidAt(LocalDateTime.of(2026, 7, 16, 11, 0))
                .confirmedAt(null)
                .canceledAt(null)
                .build();

        SellerOrderPageResponse response = SellerOrderPageResponse.builder()
                .content(List.of(summary))
                .page(0)
                .size(10)
                .totalElements(1)
                .totalPages(1)
                .build();

        when(orderService.getSellerOrders(isNull(), eq(0), eq(10)))
                .thenReturn(response);

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
