package com.openbake.order.presentation;

import com.openbake.common.response.ApiResponse;
import com.openbake.common.security.CurrentMemberProvider;
import com.openbake.order.application.OrderCancelResult;
import com.openbake.order.application.OrderConfirmResult;
import com.openbake.order.application.OrderConfirmService;
import com.openbake.order.application.OrderCreateResult;
import com.openbake.order.application.OrderDetailResult;
import com.openbake.order.application.OrderPageResult;
import com.openbake.order.application.OrderPayResult;
import com.openbake.order.application.OrderPayService;
import com.openbake.order.application.OrderService;
import com.openbake.order.presentation.dto.OrderCancelResponse;
import com.openbake.order.presentation.dto.OrderConfirmResponse;
import com.openbake.order.presentation.dto.OrderCreateRequest;
import com.openbake.order.presentation.dto.OrderCreateResponse;
import com.openbake.order.presentation.dto.OrderDetailResponse;
import com.openbake.order.presentation.dto.OrderPageResponse;
import com.openbake.order.presentation.dto.OrderPayRequest;
import com.openbake.order.presentation.dto.OrderPayResponse;
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

@Tag(name = "Order", description = "주문 생성/결제/목록/상세/취소/구매확정. 주문은 2단계입니다 — 주문서를 만드는 POST /orders 와 실제로 결제하는 POST /orders/{orderId}/pay.")
@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;
    private final OrderPayService orderPayService;
    private final OrderConfirmService orderConfirmService;
    //로그인 회원 ID 취득. payment 등 팀 다수가 쓰는 방식과 통일한다.
    private final CurrentMemberProvider currentMemberProvider;

    @Operation(
            summary = "주문 생성 (주문서 만들기 — 결제 아님)",
            description = "주문서 화면에 들어갈 때 호출합니다. 검증만 하고 PENDING 주문을 만들며 결제하지 않습니다. 경로가 셋이고 어떤 값을 보냈는지가 곧 경로입니다 — cartItemIds(장바구니), productId+quantity(바로 주문), dropId(드롭). 셋 중 하나만 보내야 합니다. 가격은 받지 않고 서버가 상품에서 읽어 스냅샷하며, 드롭은 수량도 선점값을 서버가 읽습니다. 재고는 여기서 깎지 않습니다(일반 상품은 결제 성공 직후, 드롭은 lock-start 에서 이미 깎임). 회원당 진행 중 주문은 1건이라 이미 있으면 OR006 이고, 드롭 주문만 예외로 기존 주문을 자동 만료시키고 통과합니다. reservationExpiresAt 까지 결제하지 않으면 자동 취소됩니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "C001 잘못된 요청입니다. (경로를 둘 이상 보냈거나 수량이 0 이하) / PR005 상품 타입 불일치 (바로 주문에 드롭 상품)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "ME002 유효하지 않은 인증 토큰입니다."),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "CA008 장바구니에 없는 상품입니다. / PR001 존재하지 않는 일반 상품입니다. / DR001 존재하지 않는 드롭입니다. / DR011 드롭에 참여한 기록이 없습니다."),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "OR005 픽업 날짜를 선택해야 합니다. / OR006 중복된 요청입니다. (진행 중 주문이 이미 있음 — GET /orders/pending 으로 유도) / OR009 재고 선점이 확인되지 않았습니다. / OR011 재고가 부족합니다. / CA004 선택할 수 없는 픽업 날짜입니다. / CA011 품절된 상품입니다.")
    })
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<OrderCreateResponse> create(@Valid @RequestBody OrderCreateRequest request) {
        Long memberId = currentMemberProvider.getId();
        OrderCreateResult result = orderService.create(memberId, request.toCommand());
        return ApiResponse.ok(OrderCreateResponse.from(result));
    }

    @Operation(
            summary = "결제 (예치금 차감)",
            description = "주문서에서 결제 버튼을 눌렀을 때 호출합니다. 약관 동의가 여기 있는 이유는 주문 생성 시점에는 아직 동의 전이기 때문입니다. 결제 호출 전에 주문서에 표시했던 가격과 현재 가격을 대조하고, 다르면 결제하지 않고 OR010 으로 막습니다(자동 재계산하지 않습니다). 결제가 성공하면 그 직후에 재고를 깎고, 재고가 모자라면 결제를 환불로 되돌린 뒤 outcome=OUT_OF_STOCK 으로 응답합니다. 잔액 부족 등 명시적 FAIL은 주문을 닫지 않고 PENDING으로 유지하므로 충전 후 같은 주문에서 다시 결제할 수 있습니다. 응답 타임아웃은 즉시 결제 결과를 조회하고, 아직 확정되지 않았으면 outcome=PROCESSING으로 응답합니다. Order는 결제 진행 상태를 따로 기록해 재결제·취소·만료를 금지하지 않습니다. 결제 호출 중 주문이 먼저 취소·만료된 뒤 SUCCESS가 확인되면 환불하고 outcome=PAYMENT_REVERSED로 응답합니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "OR004 약관에 동의해야 합니다. / OR008 유효하지 않은 주문 상태입니다. (PENDING 이 아니거나 주문서가 만료됨)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "ME002 유효하지 않은 인증 토큰입니다."),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "ME004 권한이 없습니다. (타인의 주문)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "OR001 존재하지 않는 주문입니다. / PR001 존재하지 않는 일반 상품입니다."),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "OR010 상품 가격이 변경되었습니다. (메시지에 변경 내역이 담깁니다)")
    })
    @PostMapping("/{orderId}/pay")
    public ApiResponse<OrderPayResponse> pay(
            @Parameter(description = "결제할 주문 ID", example = "101") @PathVariable Long orderId,
            @Valid @RequestBody OrderPayRequest request) {
        Long memberId = currentMemberProvider.getId();
        OrderPayResult result = orderPayService.pay(memberId, orderId, request.termsAgreed());
        return ApiResponse.ok(OrderPayResponse.from(result));
    }

    @Operation(
            summary = "진행 중 주문 조회",
            description = "결제하다 만 주문이 있는지 확인합니다. 주문 생성이 OR006 으로 막혔을 때 프론트가 곧바로 호출해 \"진행 중인 주문이 있습니다 · 9분 뒤 자동 취소\"를 띄우고 [이어서 결제하기] / [취소하고 새로 주문]을 고르게 하는 화면이며, 사용자가 이탈했다 돌아왔을 때도 같은 화면을 씁니다. 진행 중 주문이 없으면 data 가 null 입니다 — 정상 상태이지 오류가 아닙니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "ME002 유효하지 않은 인증 토큰입니다.")
    })
    @GetMapping("/pending")
    public ApiResponse<OrderDetailResponse> getPendingOrder() {
        Long memberId = currentMemberProvider.getId();
        return ApiResponse.ok(orderService.getPendingOrder(memberId)
                .map(OrderDetailResponse::from)
                .orElse(null));
    }

    @Operation(
            summary = "주문 목록 조회",
            description = "마이페이지 주문 내역 화면에서 호출합니다. 로그인 회원 본인의 주문만 최신순으로 페이징해 내려줍니다. PAID / CANCELED 만 나옵니다 — 구매확정은 주문 전체 상태가 아니라 각 OrderItem의 itemStatus로 확인합니다. PENDING 은 진행 중이라 별도 화면(GET /orders/pending)이고, FAILED·EXPIRED 는 사용자 입장에서 \"주문한 적이 없는\" 것이라 노출하지 않습니다. 항목이 여럿일 수 있어 대표 상품명 + 나머지 건수로 줄여 보여줍니다. size 는 50 이 상한입니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "OR008 유효하지 않은 주문 상태입니다. (PAID/CANCELED 외의 값)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "ME002 유효하지 않은 인증 토큰입니다.")
    })
    @GetMapping
    public ApiResponse<OrderPageResponse> getOrders(
            @Parameter(description = "주문 상태 필터 (미지정 시 전체): PAID / CANCELED", example = "PAID")
            @RequestParam(required = false) String orderState,
            @Parameter(description = "페이지 번호", example = "0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "페이지 크기 (최대 50, 초과 시 50으로 잘림)", example = "10")
            @RequestParam(defaultValue = "10") int size) {
        Long memberId = currentMemberProvider.getId();
        OrderPageResult result = orderService.getOrders(memberId, orderState, page, size);
        return ApiResponse.ok(OrderPageResponse.from(result));
    }

    @Operation(
            summary = "주문 상세 조회",
            description = "주문 내역에서 한 건을 눌렀을 때 호출합니다. 본인 주문만 볼 수 있고 타인 주문이면 403 입니다. 항목마다 판매자·픽업일·확정 시각이 붙어 있습니다 — 한 주문에 판매자가 여럿일 수 있기 때문입니다. 상품명/가격은 주문 시점 스냅샷이라 이후 상품이 바뀌어도 변하지 않지만, 판매자 주소·연락처는 지도·전화 버튼이 동작해야 해서 조회 시점 최신값을 씁니다."
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

    @Operation(
            summary = "주문 취소 (결제 전/후를 서버가 갈라 처리)",
            description = "하나의 API 가 주문 상태로 갈립니다 — 프론트는 지금이 결제 전인지 후인지 몰라도 됩니다. PENDING 이면 결제 시도 이력이 있어도 취소를 막지 않고 EXPIRED 로 닫습니다. 이때 환불은 없고 주문 내역에도 뜨지 않으며 드롭이면 선점 재고를 되돌립니다. 결제가 뒤늦게 SUCCESS로 확인되면 별도 보상 경로가 환불합니다. PAID 면 전액 환불하고 재고를 복구한 뒤 CANCELED 가 되며 주문 내역에 남습니다. 항목이 하나라도 구매확정됐으면 정산이 이미 나가 되돌릴 수 없으므로 OR002 로 막힙니다. 본인 주문만 취소할 수 있습니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "ME002 유효하지 않은 인증 토큰입니다."),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "ME004 권한이 없습니다. (타인의 주문)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "OR001 존재하지 않는 주문입니다. / P011 존재하지 않는 결제입니다."),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "OR002 취소할 수 없는 주문입니다. (이미 종료됐거나 항목이 확정됨) / P012 처리할 수 없는 결제 상태입니다.")
    })
    @PatchMapping("/{orderId}/cancel")
    public ApiResponse<OrderCancelResponse> cancel(
            @Parameter(description = "취소할 주문 ID", example = "101") @PathVariable Long orderId) {
        Long memberId = currentMemberProvider.getId();
        OrderCancelResult result = orderService.cancel(memberId, orderId);
        return ApiResponse.ok(OrderCancelResponse.from(result));
    }

    @Operation(
            summary = "구매 확정 (판매자, 항목 단위)",
            description = "판매자가 픽업 수령을 확인한 뒤 호출합니다. 주문이 아니라 주문 항목을 확정합니다 — 한 주문에 판매자가 여럿일 수 있고, 확정은 \"이 손님이 내 빵을 가져갔다\"는 확인이라 남의 항목까지 확정할 수 없습니다(다른 판매자 항목이면 403). member 에 seller role 이 없어 판매자 판정은 로그인 회원의 sellerId 로 합니다. 확정한 OrderItem만 CONFIRMED가 되고 Order는 PAID를 유지합니다. 모든 항목 확정이 끝나면 Payment만 최종 확정합니다. 확정된 항목마다 정산 이벤트가 발행되며, 확정된 항목이 하나라도 있으면 주문 전체를 취소할 수 없습니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "ME002 유효하지 않은 인증 토큰입니다."),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "ME004 권한이 없습니다. (판매자가 아니거나 남의 항목)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "OR001 존재하지 않는 주문입니다. / P011 존재하지 않는 결제입니다."),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "OR003 구매확정할 수 없는 주문입니다. (주문이 PAID 가 아니거나 이미 확정된 항목) / P012 처리할 수 없는 결제 상태입니다.")
    })
    @PatchMapping("/items/{orderItemId}/confirm")
    public ApiResponse<OrderConfirmResponse> confirmItem(
            @Parameter(description = "확정할 주문 항목 ID", example = "205") @PathVariable Long orderItemId) {
        OrderConfirmResult result = orderConfirmService.confirmItem(orderItemId);
        return ApiResponse.ok(OrderConfirmResponse.from(result));
    }
}
