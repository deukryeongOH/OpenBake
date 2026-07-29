package com.openbake.order.presentation;

import com.openbake.common.response.ApiResponse;
import com.openbake.order.application.OrderService;
import com.openbake.order.presentation.dto.SellerOrderPageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/sellers/me/orders")
@RequiredArgsConstructor
public class SellerOrderController {

    private final OrderService orderService;

    //판매자 본인 판매내역 목록 조회(최신순). orderState 로 상태 필터. 판매자 권한 판정은 서비스에서 한다.
    @GetMapping
    public ApiResponse<SellerOrderPageResponse> getSellerOrders(
            @RequestParam(required = false) String orderState,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.ok(orderService.getSellerOrders(orderState, page, size));
    }
}
