package com.openbake.cart.application;

import com.openbake.cart.domain.Cart;
import com.openbake.cart.domain.CartItem;
import com.openbake.cart.domain.CartRepository;
import com.openbake.cart.presentation.CartCreateRequest;
import com.openbake.cart.presentation.CartCreateResponse;
import com.openbake.cart.presentation.CartDetailResponse;
import com.openbake.cart.presentation.CartPickupDateRequest;
import com.openbake.cart.presentation.CartPickupDateResponse;
import com.openbake.common.exception.BusinessException;
import com.openbake.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CartService {

    private final CartRepository cartRepository;

    //만료 시간은 정책값이라 코드에 박지 않고 설정에서 받는다.
    @Value("${openbake.cart.ttl}")
    private Duration cartTtl;

    /**
     * 장바구니 생성. 재고를 선점하는 지점이다.
     * 주문 생성은 이미 선점된 재고를 확정만 하므로 재고를 깎는 곳은 여기 한 곳이다.
     */
    @Transactional
    public CartCreateResponse create(Long memberId, CartCreateRequest request) {
        LocalDateTime now = LocalDateTime.now();
        //기존 장바구니 처리
        // - 유효한 장바구니가 있으면 CART_ALREADY_EXISTS
        // - 만료된 장바구니면 치우고 새로 담게 해준다.
        //   (스케줄러를 기다리면 "다시 담아주세요" 안내를 받고도 못 담는 구간이 생긴다)
        Optional<Cart> found = cartRepository.findByMemberId(memberId);
        if (found.isPresent()) {
            Cart existing = found.get();

            //아직 만료되지 않았다면 사용 중인 장바구니이므로 새로 담을 수 없다.
            if (!existing.isExpired(now)) {
                throw new BusinessException(ErrorCode.CART_ALREADY_EXISTS);
            }

            //여기까지 왔으면 만료된 장바구니. 정리하고, 새로 담게 해준다.
            //삭제 전에 무엇을 얼마나 선점했는지 읽어둔다.
            CartItem expiredItem = existing.getItems();
            if (expiredItem != null) {
                Long expiredDropId = expiredItem.getDropId();
                int expiredQuantity = expiredItem.getQuantity();

                // TODO(drop): 재고 복구 — 선점했던 수량을 되돌린다
                //   UPDATE drop_inventories
                //      SET remain_quantity = remain_quantity + :expiredQuantity
                //    WHERE drop_id = :expiredDropId
            }

            //flush 까지 해야 아래 INSERT 가 member_id UNIQUE 제약에 걸리지 않는다.
            cartRepository.delete(existing);
            cartRepository.flush();
        }

        // TODO(drop): 드롭 존재 여부 · 판매 기간 · 드롭 상태 검증
        //   없으면 DROP_NOT_FOUND(404), 오픈 전이거나 종료됐으면 DROP_NOT_ON_SALE(409)

        // TODO(drop): 1인 구매 제한 검증 — 초과 시 PER_PERSON_LIMIT_EXCEEDED(409)
        //   drop_meta_data.limit_quantity 와 비교한다. 기존 유효 주문 수량 합산은 order 도메인 완성 후.

        // TODO(drop): 재고 선점 — 분산락 + 조건부 UPDATE
        //   UPDATE drop_inventories
        //      SET remain_quantity = remain_quantity - :quantity
        //    WHERE drop_id = :dropId AND remain_quantity >= :quantity
        //   갱신 행 수가 0이면 SOLD_OUT(409).
        //   조회 후 차감하면 두 요청이 모두 통과해 오버셀이 나므로 쓰지 않는다.
        Cart cart = Cart.create(memberId, now.plus(cartTtl));
        cart.addItem(CartItem.create(request.getDropId(), request.getQuantity()));

        Cart saved;
        try {
            //saveAndFlush 로 즉시 INSERT 해야 UNIQUE 위반을 이 자리에서 잡을 수 있다.
            //save 만 쓰면 커밋 시점에 flush 돼 try 밖에서 터진다.
            saved = cartRepository.saveAndFlush(cart);
        } catch (DataIntegrityViolationException e) {
            //carts.member_id UNIQUE 위반.
            //더블클릭 등으로 두 요청이 위 기존 장바구니 조회를 함께 통과한 경우다.
            //선검사만으로는 동시 요청을 막을 수 없어 DB 제약이 최종 방어선이 된다.
            throw new BusinessException(ErrorCode.CART_ALREADY_EXISTS);
        }

        return CartCreateResponse.builder()
                .cartId(saved.getCartId())
                .dropId(request.getDropId())
                .quantity(request.getQuantity())
                .expiresAt(saved.getExpiresAt())
                .createdAt(saved.getCreatedAt())
                .build();
    }

    /**
     * 장바구니 조회. 만료된 장바구니는 CART_EXPIRED 로 막는다(배치가 아직 안 지운 구간 방어).
     */
    @Transactional(readOnly = true)
    public CartDetailResponse getCart(Long memberId) {
        LocalDateTime now = LocalDateTime.now();

        // 1. memberId 로 장바구니 조회 → 없으면 CART_NOT_FOUND
        Cart cart = cartRepository.findByMemberId(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CART_NOT_FOUND));

        // 2. 만료됐으면 CART_EXPIRED (배치가 아직 안 지운 구간 방어)
        if (cart.isExpired(now)) {
            throw new BusinessException(ErrorCode.CART_EXPIRED);
        }

        // 3. 만료까지 남은 초 (expiresAt - now)
        long remainingSeconds = Duration.between(now, cart.getExpiresAt()).getSeconds();

        // 4. 응답 조립
        return CartDetailResponse.builder()
                .cartId(cart.getCartId())
                .quantity(cart.getItems().getQuantity())
                .selectedPickupDate(cart.getPickupDate())
                .expiresAt(cart.getExpiresAt())
                .remainingSeconds((int) remainingSeconds)
                // --- 아래는 타 도메인이라 지금은 못 채움 ---
                // TODO(drop): drop(dropId, dropName, price, imageUrl)
                // TODO(seller): seller(sellerId, sellerName)
                // TODO(drop): estimatedAmount = price * quantity
                // TODO(drop): pickupDates = 드롭의 픽업 가능일 (지난 날짜 제외)
                .build();
    }

    /**
     * 픽업 날짜 선택. 재선택 시 덮어쓴다.
     */
    @Transactional
    public CartPickupDateResponse updatePickupDate(Long memberId, CartPickupDateRequest request) {
        LocalDateTime now = LocalDateTime.now();
        LocalDate pickupDate = request.getPickupDate();

        // 1. 조회 → 없으면 CART_NOT_FOUND
        Cart cart = cartRepository.findByMemberId(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CART_NOT_FOUND));

        // 2. 만료됐으면 CART_EXPIRED
        if (cart.isExpired(now)) {
            throw new BusinessException(ErrorCode.CART_EXPIRED);
        }

        // 3. 지난 날짜 방어 — 오늘보다 이전이면 선택 불가 (drop 정보 없이 판정 가능)
        if (pickupDate.isBefore(now.toLocalDate())) {
            throw new BusinessException(ErrorCode.CART_PICKUP_DATE_UNAVAILABLE);
        }

        // TODO(drop): 이 날짜가 드롭의 픽업 가능일(pickup_available_date)에 포함되는지 검증
        //   아니면 CART_INVALID_PICKUP_DATE (위조 요청 방어)

        // 4. 저장. 관리 상태 엔티티라 변경 감지로 커밋 시 자동 반영(별도 save 불필요).
        cart.updatePickupDate(pickupDate);

        return CartPickupDateResponse.builder()
                .cartId(cart.getCartId())
                .pickupDate(pickupDate)
                .build();
    }

    /**
     * 장바구니 삭제. 선점했던 재고를 복구한다.
     * 만료된 장바구니도 삭제 대상이므로 여기서는 CART_EXPIRED 를 던지지 않는다.
     */
    @Transactional
    public void deleteCart(Long memberId) {
        Cart cart = cartRepository.findByMemberId(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CART_NOT_FOUND));

        //삭제 전에 무엇을 얼마나 선점했는지 읽어둔다. 지우고 나면 복구할 값을 알 수 없다.
        CartItem item = cart.getItems();
        if (item != null) {
            Long dropId = item.getDropId();
            int quantity = item.getQuantity();

            // TODO(drop): 재고 복구 — 선점했던 수량을 되돌린다
            //   UPDATE drop_inventories
            //      SET remain_quantity = remain_quantity + :quantity
            //    WHERE drop_id = :dropId
            //   차감과 달리 조건절이 필요 없다. 복구는 실패하면 안 되는 연산이다.
        }

        //cart_items 는 cascade = ALL + orphanRemoval 이라 함께 삭제된다.
        cartRepository.delete(cart);
    }

    /**
     * 만료된 장바구니를 일괄 정리한다. 선점 재고를 복구하고 삭제한다.
     * DELETE 를 호출하지 않고 이탈한 경우(브라우저 종료 등)의 실질적인 재고 회수 수단이다.
     *
     * @return 정리한 장바구니 수
     */
    @Transactional
    public int expireCarts(LocalDateTime now) {
        List<Cart> expiredCarts = cartRepository.findAllByExpiresAtLessThanEqual(now);
        if (expiredCarts.isEmpty()) {
            return 0;
        }

        for (Cart cart : expiredCarts) {
            //삭제 전에 선점 정보를 읽어둔다. 벌크 삭제를 쓰면 이 값을 잃어 재고를 되돌릴 수 없다.
            CartItem item = cart.getItems();
            if (item != null) {
                Long dropId = item.getDropId();
                int quantity = item.getQuantity();

                // TODO(drop): 재고 복구 — 선점했던 수량을 되돌린다
                //   UPDATE drop_inventories
                //      SET remain_quantity = remain_quantity + :quantity
                //    WHERE drop_id = :dropId
            }
        }

        cartRepository.deleteAll(expiredCarts);
        return expiredCarts.size();
    }
}
