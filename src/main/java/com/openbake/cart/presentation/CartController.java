package com.openbake.cart.presentation;

import com.openbake.cart.application.CartCreateResult;
import com.openbake.cart.application.CartDetailResult;
import com.openbake.cart.application.CartPickupDateResult;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Cart", description = "장바구니 생성/조회/픽업 날짜 선택/삭제. 드롭 상세에서 담기(재고 선점) 이후 결제 직전까지의 구간을 담당합니다.")
@RestController
@RequestMapping("/api/v1/cart")
@RequiredArgsConstructor
public class CartController {

    private final CartService cartService;
    //로그인 회원 ID 취득. payment 등 팀 다수가 쓰는 방식과 통일한다.
    private final CurrentMemberProvider currentMemberProvider;

    //경로에 cartId 가 없다. 대상 장바구니는 로그인 회원으로 특정한다.
    //재고 차감은 drop 이 담기 시점에 이미 처리한다. 여기서는 선점 확인 후 장바구니만 생성한다.
    @Operation(
            summary = "장바구니 생성",
            description = "드롭 상세에서 담기(재고 선점)를 통과한 직후 호출합니다. 재고는 담기 시점에 drop이 이미 선점(DropEntry=RESERVED)했고, 여기서는 그 선점이 실제로 있는지 확인한 뒤 만료 시각이 붙은 장바구니를 만듭니다. 선점 없이 바로 호출하면 CA006으로 막힙니다. 장바구니는 회원당 1개이며 경로에 cartId가 없고 토큰의 회원으로 대상을 특정합니다. 유효한 장바구니가 이미 있으면 CA001이지만, 이전 장바구니가 만료 상태면 그 선점 재고를 drop에 복구시키고 치운 뒤 새로 만들어 줍니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "C001 잘못된 요청입니다. (dropid 누락, quantity < 1)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "ME002 유효하지 않은 인증 토큰입니다."),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "CA001 이미 장바구니에 담긴 상품이 있습니다. / CA006 재고 선점이 확인되지 않았습니다. (담기 흐름을 거치지 않았거나 선점이 이미 회수됨) / CA007 선점한 수량과 요청 수량이 다릅니다."),
    })
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CartCreateResponse> create(@Valid @RequestBody CartCreateRequest request) {
        Long memberId = currentMemberProvider.getId();
        CartCreateResult result = cartService.create(memberId, request.getDropId(), request.getQuantity());
        CartCreateResponse response = CartCreateResponse.from(result);
        return ApiResponse.ok(response);
    }

    //장바구니 조회. 대상은 로그인 회원으로 특정한다. 상태 200.
    @Operation(
            summary = "장바구니 조회",
            description = "장바구니 화면을 그릴 때 호출합니다. 상품명/가격/판매자/예상 결제 금액/선택 가능한 픽업 날짜/만료까지 남은 초를 한 번에 내려줍니다. estimatedAmount와 price는 스냅샷이 아니라 조회 시점의 드롭 최신값이므로, 선점 중 판매자가 가격을 바꾸면 값이 달라질 수 있습니다. 만료 배치가 아직 지우지 않은 장바구니는 CA003으로 구분해 응답합니다(행 자체가 없으면 CA002)."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "ME002 유효하지 않은 인증 토큰입니다."),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "CA002 장바구니가 없습니다. / DR001 존재하지 않는 드롭입니다."),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "CA003 장바구니가 만료되었습니다. 다시 담아주세요.")
    })
    @GetMapping
    public ApiResponse<CartDetailResponse> getCart() {
        Long memberId = currentMemberProvider.getId();
        CartDetailResult result = cartService.getCart(memberId);
        CartDetailResponse response = CartDetailResponse.from(result);
        return ApiResponse.ok(response);
    }

    //픽업 날짜 선택. 상태 200.
    @Operation(
            summary = "픽업 날짜 선택",
            description = "장바구니 화면에서 픽업 날짜를 고를 때 호출합니다. 재선택하면 기존 값을 덮어씁니다. 주문 생성(결제)은 픽업 날짜가 선택돼 있어야 통과하므로, 결제 테스트 전에 반드시 한 번 호출해야 합니다. 화면 목록은 조회 API가 내려주지만 요청 본문은 클라이언트가 만든 값이라, 서버가 드롭의 픽업 가능일에 실제로 포함되는지 다시 확인합니다(위조/stale 방어 → CA004)."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "C001 잘못된 요청입니다. (pickupDate 누락) / CA004 선택할 수 없는 픽업 날짜입니다. (해당 드롭의 픽업 가능일이 아님)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "ME002 유효하지 않은 인증 토큰입니다."),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "CA002 장바구니가 없습니다. / DR001 존재하지 않는 드롭입니다."),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "CA003 장바구니가 만료되었습니다. / CA005 이미 지난 픽업 날짜입니다.")
    })
    @PatchMapping("/pickup-date")
    public ApiResponse<CartPickupDateResponse> updatePickupDate(@Valid @RequestBody CartPickupDateRequest request) {
        Long memberId = currentMemberProvider.getId();
        CartPickupDateResult result = cartService.updatePickupDate(memberId, request.getPickupDate());
        CartPickupDateResponse response = CartPickupDateResponse.from(result);
        return ApiResponse.ok(response);
    }

    //장바구니 삭제(재고 복구). 본문 없이 204 를 반환한다.
    @Operation(
            summary = "장바구니 삭제 (재고 복구)",
            description = "사용자가 결제를 포기하고 장바구니를 비울 때 호출합니다. 담기 때 선점된 재고를 drop에 복구시킨 뒤 장바구니를 삭제하며, 응답 본문 없이 204를 반환합니다. 조회/픽업 날짜 선택과 달리 만료된 장바구니도 정상 삭제 대상이므로 CA003을 던지지 않습니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "ME002 유효하지 않은 인증 토큰입니다."),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "CA002 장바구니가 없습니다.")
    })
    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteCart() {
        Long memberId = currentMemberProvider.getId();
        cartService.deleteCart(memberId);
    }
}
