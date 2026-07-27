package com.openbake.cart.presentation;

import com.openbake.cart.application.CartService;
import com.openbake.common.response.ApiResponse;
import com.openbake.common.security.CurrentMemberProvider;
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

@RestController
@RequestMapping("/api/v1/cart")
@RequiredArgsConstructor
public class CartController {

    private final CartService cartService;
    //로그인 회원 ID 취득. payment 등 팀 다수가 쓰는 방식과 통일한다.
    private final CurrentMemberProvider currentMemberProvider;

    //경로에 cartId 가 없다. 대상 장바구니는 로그인 회원으로 특정한다.
    //재고 차감은 내부에서 drop 에 요청하고, 성공하면 장바구니를 생성한다.
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CartCreateResponse> create(@Valid @RequestBody CartCreateRequest request) {
        Long memberId = currentMemberProvider.getId();
        return ApiResponse.ok(cartService.create(memberId, request));
    }

    //장바구니 조회. 대상은 로그인 회원으로 특정한다. 상태 200.
    @GetMapping
    public ApiResponse<CartDetailResponse> getCart() {
        Long memberId = currentMemberProvider.getId();
        return ApiResponse.ok(cartService.getCart(memberId));
    }

    //픽업 날짜 선택. 상태 200.
    @PatchMapping("/pickup-date")
    public ApiResponse<CartPickupDateResponse> updatePickupDate(@Valid @RequestBody CartPickupDateRequest request) {
        Long memberId = currentMemberProvider.getId();
        return ApiResponse.ok(cartService.updatePickupDate(memberId, request));
    }

    //장바구니 삭제(재고 복구). 본문 없이 204 를 반환한다.
    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteCart() {
        Long memberId = currentMemberProvider.getId();
        cartService.deleteCart(memberId);
    }
}
