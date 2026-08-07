package com.openbake.order.presentation;

import com.openbake.common.response.ApiResponse;
import com.openbake.common.security.CurrentMemberProvider;
import com.openbake.order.application.OrderCancelResult;
import com.openbake.order.application.OrderConfirmResult;
import com.openbake.order.application.OrderCreateResult;
import com.openbake.order.application.OrderDetailResult;
import com.openbake.order.application.OrderPageResult;
import com.openbake.order.application.OrderService;
import com.openbake.order.presentation.dto.OrderCancelResponse;
import com.openbake.order.presentation.dto.OrderConfirmResponse;
import com.openbake.order.presentation.dto.OrderCreateRequest;
import com.openbake.order.presentation.dto.OrderCreateResponse;
import com.openbake.order.presentation.dto.OrderDetailResponse;
import com.openbake.order.presentation.dto.OrderPageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Order", description = "주문 생성(예치금 결제)/목록/상세/취소/구매확정. 장바구니가 만들어진 뒤부터 정산 대상이 되기까지의 구간을 담당합니다.")
@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;
    //로그인 회원 ID 취득. payment 등 팀 다수가 쓰는 방식과 통일한다.
    private final CurrentMemberProvider currentMemberProvider;

    //주문 생성(결제). 대상 장바구니는 로그인 회원으로 특정한다.
    @Operation(
            summary = "주문 생성 (예치금 결제)",
            description = "장바구니 화면에서 결제 버튼을 눌렀을 때 호출합니다. 본문에는 약관 동의만 담기고 주문 대상(드롭/수량/픽업 날짜)은 서버가 회원의 장바구니에서 읽으므로, 미리 장바구니 생성과 픽업 날짜 선택이 끝나 있어야 합니다. 주문 저장 → 예치금 차감 → 장바구니 삭제가 한 트랜잭션이라 잔액 부족 등으로 결제가 실패하면 주문도 남지 않으며, 이때 담기 때 선점된 재고는 복구되고 장바구니도 정리됩니다. 상품명/가격은 주문 시점 값으로 스냅샷되어 이후 드롭이 바뀌어도 주문 내역은 그대로입니다. 재고는 담기 시점에 이미 선점됐으므로 여기서 다시 깎지 않습니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "C001 잘못된 요청입니다. / OR004 약관에 동의해야 합니다. / P010 예치금 잔액이 부족합니다."),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "ME002 유효하지 않은 인증 토큰입니다."),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "CA002 장바구니가 없습니다. / DR001 존재하지 않는 드롭입니다."),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "CA003 장바구니가 만료되었습니다. (선점 재고가 이미 회수됨) / OR005 픽업 날짜를 선택해야 합니다.")
    })
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<OrderCreateResponse> create(@Valid @RequestBody OrderCreateRequest request) {
        Long memberId = currentMemberProvider.getId();
        OrderCreateResult result = orderService.create(memberId, request.getTermsAgreed());
        return ApiResponse.ok(OrderCreateResponse.from(result));
    }

    //주문 목록 조회(본인, 최신순). orderState 로 상태 필터. 상태 200.
    @Operation(
            summary = "주문 목록 조회",
            description = "마이페이지 주문 내역 화면에서 호출합니다. 로그인한 회원 본인의 주문만 최신순으로 페이징해 내려주며, orderState로 상태를 걸러볼 수 있습니다. 목록의 상품명/수량은 주문 시점 스냅샷이고 판매자명은 조회 시점에 판매자에서 읽습니다. size는 50이 상한이며 더 큰 값을 보내면 50으로 잘립니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "OR008 유효하지 않은 주문 상태입니다. (PAID/CONFIRMED/CANCELED 외의 값)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "ME002 유효하지 않은 인증 토큰입니다.")
    })
    @GetMapping
    public ApiResponse<OrderPageResponse> getOrders(
            @Parameter(description = "주문 상태 필터 (미지정 시 전체): PAID / CONFIRMED / CANCELED", example = "PAID")
            @RequestParam(required = false) String orderState,
            @Parameter(description = "페이지 번호", example = "0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "페이지 크기 (최대 50, 초과 시 50으로 잘림)", example = "10")
            @RequestParam(defaultValue = "10") int size) {
        Long memberId = currentMemberProvider.getId();
        OrderPageResult result = orderService.getOrders(memberId, orderState, page, size);
        return ApiResponse.ok(OrderPageResponse.from(result));
    }

    //주문 상세 조회(본인만). 상태 200.
    @Operation(
            summary = "주문 상세 조회",
            description = "주문 내역에서 한 건을 눌렀을 때 호출합니다. 본인 주문만 볼 수 있고 타인 주문을 조회하면 403입니다. 상품명/가격/수량은 주문 시점 스냅샷이므로 판매자가 드롭을 수정해도 값이 변하지 않습니다. 상태별로 paidAt / confirmedAt / canceledAt 중 해당하는 시각만 채워지고 나머지는 null입니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "ME002 유효하지 않은 인증 토큰입니다."),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "ME004 권한이 없습니다. (타인의 주문)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "OR001 존재하지 않는 주문입니다.")
    })
    @GetMapping("/{orderId}")
    public ApiResponse<OrderDetailResponse> getOrderDetail(
            @Parameter(description = "주문 ID", example = "101") @PathVariable Long orderId) {
        Long memberId = currentMemberProvider.getId();
        OrderDetailResult result = orderService.getOrderDetail(memberId, orderId);
        return ApiResponse.ok(OrderDetailResponse.from(result));
    }

    //주문 취소(본인). 전액 환불 + 재고 복구. 상태 200.
    @Operation(
            summary = "주문 취소 (전액 환불 + 재고 복구)",
            description = "구매자가 주문 상세에서 취소를 눌렀을 때 호출합니다. 결제 금액을 예치금으로 전액 환불하고 선점했던 재고를 드롭에 되돌리는 것까지 한 트랜잭션에서 처리합니다. PAID 상태에서만 가능하므로 이미 구매확정된 주문이나 취소된 주문(중복 취소 포함)은 OR002로 막힙니다. 본인 주문만 취소할 수 있습니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "ME002 유효하지 않은 인증 토큰입니다."),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "ME004 권한이 없습니다. (타인의 주문)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "OR001 존재하지 않는 주문입니다. / P011 존재하지 않는 결제입니다."),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "OR002 취소할 수 없는 주문입니다. (이미 CONFIRMED/CANCELED) / P012 처리할 수 없는 결제 상태입니다.")
    })
    @PatchMapping("/{orderId}/cancel")
    public ApiResponse<OrderCancelResponse> cancel(
            @Parameter(description = "취소할 주문 ID", example = "101") @PathVariable Long orderId) {
        Long memberId = currentMemberProvider.getId();
        OrderCancelResult result = orderService.cancel(memberId, orderId);
        return ApiResponse.ok(OrderCancelResponse.from(result));
    }

    //구매 확정(판매자). 해당 주문의 판매자만 가능. 판매자 판정은 서비스에서 CurrentSellerProvider 로 한다.
    @Operation(
            summary = "구매 확정 (판매자)",
            description = "판매자가 픽업 수령을 확인한 뒤 호출합니다. 구매자가 아니라 해당 주문의 판매자만 호출할 수 있고, member에 seller role이 없어 로그인 회원의 sellerId와 주문의 sellerId를 대조해 판정합니다(다른 판매자면 403). 주문 상태만 바꾸는 API가 아닙니다 — 결제 상태도 CONFIRMED로 함께 전이하고, 커밋 후 정산으로 구매확정 이벤트가 발행되어 정산 대상이 만들어집니다. 확정 후에는 취소/환불이 불가합니다. PAID 상태에서만 가능하므로 자동확정 배치가 이미 처리한 주문에 다시 요청하면 OR003입니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "ME002 유효하지 않은 인증 토큰입니다."),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "ME004 권한이 없습니다. (판매자가 아니거나 해당 주문의 판매자가 아님)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "OR001 존재하지 않는 주문입니다. / P011 존재하지 않는 결제입니다."),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "OR003 구매확정할 수 없는 주문입니다. (이미 CONFIRMED/CANCELED) / P012 처리할 수 없는 결제 상태입니다.")
    })
    @PatchMapping("/{orderId}/confirm")
    public ApiResponse<OrderConfirmResponse> confirm(
            @Parameter(description = "확정할 주문 ID", example = "101") @PathVariable Long orderId) {
        OrderConfirmResult result = orderService.confirm(orderId);
        return ApiResponse.ok(OrderConfirmResponse.from(result));
    }
}
