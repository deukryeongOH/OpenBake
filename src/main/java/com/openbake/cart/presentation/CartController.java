package com.openbake.cart.presentation;

import com.openbake.cart.application.CartDetailResult;
import com.openbake.cart.application.CartItemAddResult;
import com.openbake.cart.application.CartService;
import com.openbake.common.response.ApiResponse;
import com.openbake.common.security.CurrentMemberProvider;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Cart", description = "일반 상품 장바구니. 담기/조회/수량·픽업일 변경/삭제. 드롭 상품은 장바구니를 거치지 않고 바로 주문으로 갑니다.")
@RestController
@RequestMapping("/api/v1/cart")
@RequiredArgsConstructor
public class CartController {

    private final CartService cartService;
    //로그인 회원 ID 취득. payment 등 팀 다수가 쓰는 방식과 통일한다.
    private final CurrentMemberProvider currentMemberProvider;

    //경로에 cartId 가 없다. 대상 장바구니는 로그인 회원으로 특정한다.
    @Operation(
            summary = "장바구니에 상품 담기",
            description = "일반 상품을 장바구니에 담습니다. 장바구니는 회원당 1개이며 없으면 이때 만들어집니다. 재고를 선점하지 않으므로 담아둔 뒤에도 다른 사람이 살 수 있고, 실제 재고 확보는 결제 시점에 일어납니다. 이미 담은 상품을 또 담으면 행이 늘지 않고 수량이 합산되며, 픽업 날짜를 이번에 골랐다면 그 값으로 덮어씁니다. 재고 검사는 요청 수량이 아니라 합산 후 수량으로 합니다. 픽업 날짜는 담을 때 고르지 않아도 되지만 주문으로 넘어가려면 반드시 선택돼 있어야 합니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "C001 잘못된 요청입니다. (productId 누락, quantity < 1) / CA004 선택할 수 없는 픽업 날짜입니다. / PR005 일반 상품이 아닙니다. (드롭 상품은 장바구니를 거치지 않습니다)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "ME002 유효하지 않은 인증 토큰입니다."),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "PR001 존재하지 않는 일반 상품입니다."),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "CA005 이미 지난 픽업 날짜입니다. / CA009 남은 재고보다 많은 수량을 담을 수 없습니다. / CA011 품절된 상품입니다.")
    })
    @PostMapping("/items")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CartItemResponse> addItem(@Valid @RequestBody CartItemAddRequest request) {
        Long memberId = currentMemberProvider.getId();
        CartItemAddResult result = cartService.addItem(
                memberId, request.getProductId(), request.getQuantity(), request.getPickUpDate());
        return ApiResponse.ok(CartItemResponse.from(result));
    }

    @Operation(
            summary = "장바구니 조회",
            description = "장바구니 화면을 그릴 때 호출합니다. 장바구니를 만든 적이 없거나 비어 있어도 200으로 빈 목록을 내려줍니다. 상품명·가격·이미지·재고·픽업 가능일·판매자 상호명은 스냅샷이 아니라 조회 시점의 최신값이라, 담아둔 뒤 판매자가 가격이나 상호를 바꾸면 값이 달라집니다. 가격이 담을 때와 달라진 항목은 priceChanged=true와 담을 때 단가(addedPrice)를 함께 내려주므로 'addedPrice → price' 형태로 변동 폭을 안내할 수 있습니다(오른 경우와 내린 경우 모두). 결제 금액은 언제나 최신 price 기준입니다. 상품이 삭제됐거나, 재고가 담아둔 수량보다 적거나, 픽업 날짜를 아직 고르지 않았거나(PICKUP_DATE_UNSELECTED), 고른 픽업 날짜가 더 이상 선택 가능일이 아닌 항목은 orderable=false와 status로 사유를 함께 내려주니 프론트가 해당 항목과 주문 버튼을 비활성 처리하면 됩니다. 픽업 날짜는 담을 때는 고르지 않아도 되지만 주문으로 넘기려면 반드시 선택돼 있어야 하므로, 미선택 항목은 주문 대상으로 체크할 수 없게 막으면 됩니다. 판매자가 픽업 가능일을 추가·수정하면 pickUpAvailableDates에 바로 반영되므로 그중에서 다시 고르면 됩니다. totalAmount는 주문 가능한 항목만 합산합니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "ME002 유효하지 않은 인증 토큰입니다.")
    })
    @GetMapping
    public ApiResponse<CartDetailResponse> getCart() {
        Long memberId = currentMemberProvider.getId();
        CartDetailResult result = cartService.getCart(memberId);
        return ApiResponse.ok(CartDetailResponse.from(result));
    }

    @Operation(
            summary = "장바구니 항목 수량 변경",
            description = "장바구니 화면에서 수량을 고칠 때 호출합니다. 담기와 달리 더하는 게 아니라 요청한 값으로 교체하므로, 현재 재고를 넘으면 CA009로 막힙니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "C001 잘못된 요청입니다. (quantity < 1)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "ME002 유효하지 않은 인증 토큰입니다."),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "CA002 장바구니가 없습니다. / CA008 장바구니에 없는 상품입니다. / PR001 존재하지 않는 일반 상품입니다."),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "CA009 남은 재고보다 많은 수량을 담을 수 없습니다.")
    })
    @PatchMapping("/items/{cartItemId}/quantity")
    public ApiResponse<CartItemResponse> updateQuantity(
            @PathVariable Long cartItemId,
            @Valid @RequestBody CartItemQuantityRequest request) {
        Long memberId = currentMemberProvider.getId();
        CartItemAddResult result = cartService.updateQuantity(memberId, cartItemId, request.getQuantity());
        return ApiResponse.ok(CartItemResponse.from(result));
    }

    @Operation(
            summary = "장바구니 항목 픽업 날짜 선택",
            description = "장바구니 화면에서 픽업 날짜를 고르거나 바꿀 때 호출합니다. 항목별로 따로 고릅니다. 재선택하면 덮어씁니다. 화면 목록은 조회 API가 내려주지만 요청 본문은 클라이언트가 만든 값이라, 서버가 상품의 픽업 가능일에 실제로 포함되는지 다시 확인합니다(위조/stale 방어 → CA004)."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "C001 잘못된 요청입니다. (pickUpDate 누락) / CA004 선택할 수 없는 픽업 날짜입니다."),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "ME002 유효하지 않은 인증 토큰입니다."),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "CA002 장바구니가 없습니다. / CA008 장바구니에 없는 상품입니다. / PR001 존재하지 않는 일반 상품입니다."),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "CA005 이미 지난 픽업 날짜입니다.")
    })
    @PatchMapping("/items/{cartItemId}/pickup-date")
    public ApiResponse<CartItemResponse> updatePickUpDate(
            @PathVariable Long cartItemId,
            @Valid @RequestBody CartItemPickUpDateRequest request) {
        Long memberId = currentMemberProvider.getId();
        CartItemAddResult result = cartService.updatePickUpDate(memberId, cartItemId, request.getPickUpDate());
        return ApiResponse.ok(CartItemResponse.from(result));
    }

    @Operation(
            summary = "장바구니 항목 삭제",
            description = "항목 하나만 지웁니다. 장바구니 자체는 남으므로 이후에도 장바구니 페이지에 들어갈 수 있습니다. 재고를 선점하지 않았으므로 복구할 것도 없습니다. 응답 본문 없이 204를 반환합니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "ME002 유효하지 않은 인증 토큰입니다."),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "CA002 장바구니가 없습니다. / CA008 장바구니에 없는 상품입니다.")
    })
    @DeleteMapping("/items/{cartItemId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeItem(@PathVariable Long cartItemId) {
        Long memberId = currentMemberProvider.getId();
        cartService.removeItem(memberId, cartItemId);
    }

    @Operation(
            summary = "장바구니 비우기",
            description = "담긴 항목을 모두 지웁니다. 장바구니 행 자체는 남습니다. 응답 본문 없이 204를 반환합니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "ME002 유효하지 않은 인증 토큰입니다."),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "CA002 장바구니가 없습니다.")
    })
    @DeleteMapping("/items")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void clearItems() {
        Long memberId = currentMemberProvider.getId();
        cartService.clearItems(memberId);
    }
}
