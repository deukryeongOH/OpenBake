package com.openbake.order.presentation;

import com.openbake.common.response.ApiResponse;
import com.openbake.order.application.OrderService;
import com.openbake.order.presentation.dto.OrderCancelResponse;
import com.openbake.order.presentation.dto.OrderConfirmResponse;
import com.openbake.order.presentation.dto.OrderCreateRequest;
import com.openbake.order.presentation.dto.OrderCreateResponse;
import com.openbake.order.presentation.dto.OrderDetailResponse;
import com.openbake.order.presentation.dto.OrderPageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    //주문 생성(결제). 대상 장바구니는 인증 토큰의 회원으로 특정한다.
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<OrderCreateResponse> create(
            @AuthenticationPrincipal Long memberId,
            @Valid @RequestBody OrderCreateRequest request) {
        return ApiResponse.ok(orderService.create(memberId, request));
    }

    //주문 목록 조회(본인, 최신순). orderState 로 상태 필터. 상태 200.
    @GetMapping
    public ApiResponse<OrderPageResponse> getOrders(
            @AuthenticationPrincipal Long memberId,
            @RequestParam(required = false) String orderState,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.ok(orderService.getOrders(memberId, orderState, page, size));
    }

    //주문 상세 조회(본인만). 상태 200.
    @GetMapping("/{orderId}")
    public ApiResponse<OrderDetailResponse> getOrderDetail(
            @AuthenticationPrincipal Long memberId,
            @PathVariable Long orderId) {
        return ApiResponse.ok(orderService.getOrderDetail(memberId, orderId));
    }

    //주문 취소(본인). 전액 환불 + 재고 복구. 상태 200.
    @PatchMapping("/{orderId}/cancel")
    public ApiResponse<OrderCancelResponse> cancel(
            @AuthenticationPrincipal Long memberId,
            @PathVariable Long orderId) {
        return ApiResponse.ok(orderService.cancel(memberId, orderId));
    }

    //구매 확정(판매자). 해당 주문의 판매자만 가능. 상태 200.
    @PatchMapping("/{orderId}/confirm")
    public ApiResponse<OrderConfirmResponse> confirm(@PathVariable Long orderId) {
        return ApiResponse.ok(orderService.confirm(orderId));
    }
}
