package com.openbake.cart.application;

import com.openbake.cart.domain.Cart;
import com.openbake.cart.domain.CartItem;
import com.openbake.cart.domain.CartRepository;
import com.openbake.common.exception.BusinessException;
import com.openbake.common.exception.ErrorCode;
import com.openbake.drop.application.DropLockService;
import com.openbake.drop.domain.Drop;
import com.openbake.drop.domain.DropEntry;
import com.openbake.drop.domain.DropEntryRepository;
import com.openbake.drop.domain.DropRepository;
import com.openbake.drop.domain.EntryStatus;
import com.openbake.seller.domain.Seller;
import com.openbake.seller.domain.SellerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CartService {

    private final CartRepository cartRepository;
    private final DropRepository dropRepository;
    private final SellerRepository sellerRepository;
    //재고 선점 여부 확인용(담기 시 drop 이 만든 응모 상태를 조회).
    private final DropEntryRepository dropEntryRepository;
    //재고 복구 요청용(이탈 시 동기 호출). 차감은 cart 가 하지 않는다.
    private final DropLockService dropLockService;

    //만료 시간은 정책값이라 코드에 박지 않고 설정에서 받는다.
    @Value("${openbake.cart.ttl}")
    private Duration cartTtl;

    /**
     * 장바구니 생성.
     * 재고 차감은 cart 가 하지 않는다. 드롭 상세에서 "담기"를 누르면 drop 이 재고를 선점/차감하고,
     * 그 뒤 이 API 가 호출돼 장바구니를 만든다. cart 는 drop 이 실제로 선점했는지(DropEntry=RESERVED)만
     * 확인하고 장바구니를 기록한다.
     */
    @Transactional
    public CartCreateResult create(Long memberId, Long dropId, Integer quantity) {
        LocalDateTime now = LocalDateTime.now();

        //기존 장바구니 처리
        // 유효한 장바구니가 있으면 CART_ALREADY_EXISTS
        // 만료된 장바구니면 선점 재고를 복구시키고 치운 뒤 새로 담게 해준다.
        Optional<Cart> found = cartRepository.findByMemberId(memberId);
        if (found.isPresent()) {
            Cart existing = found.get();

            //아직 만료되지 않았다면 사용 중인 장바구니이므로 새로 담을 수 없다.
            if (!existing.isExpired(now)) {
                throw new BusinessException(ErrorCode.CART_ALREADY_EXISTS);
            }

            //만료된 장바구니. 선점 재고를 drop 에 복구시키고 정리한다.
            //(삭제 전에 dropId/quantity 를 읽어야 하므로 먼저 복구 요청을 보낸다)
            rollbackStockIfPresent(existing);
            cartRepository.delete(existing);
            //flush 까지 해야 아래 INSERT 가 member_id UNIQUE 제약에 걸리지 않는다.
            cartRepository.flush();
        }

        //재고 선점 확인 — drop 이 담기 시점에 선점(RESERVED)했는지 확인한다.
        //응모가 없거나 RESERVED 가 아니면 정상적인 담기 흐름을 거치지 않은 것이므로 막는다.
        DropEntry entry = dropEntryRepository.findByDropIdAndMemberId(dropId, memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CART_STOCK_NOT_RESERVED));
        if (entry.getEntryStatus() != EntryStatus.RESERVED) {
            throw new BusinessException(ErrorCode.CART_STOCK_NOT_RESERVED);
        }

        Cart cart = Cart.create(memberId, now.plus(cartTtl));
        cart.addItem(CartItem.create(dropId, quantity));

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
        return CartCreateResult.from(saved);
    }

    /**
     * 장바구니 조회. 만료된 장바구니는 CART_EXPIRED 로 막는다(배치가 아직 안 지운 구간 방어).
     * 드롭/판매자 정보는 조회 시점에 drop/seller 에서 읽어 채운다(스냅샷 아님).
     */
    @Transactional(readOnly = true)
    public CartDetailResult getCart(Long memberId) {
        LocalDateTime now = LocalDateTime.now();

        // 1. memberId 로 장바구니 조회 → 없으면 CART_NOT_FOUND
        Cart cart = cartRepository.findByMemberId(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CART_NOT_FOUND));

        // 2. 만료됐으면 CART_EXPIRED (배치가 아직 안 지운 구간 방어)
        if (cart.isExpired(now)) {
            throw new BusinessException(ErrorCode.CART_EXPIRED);
        }

        CartItem item = cart.getItems();
        int quantity = item.getQuantity();

        // 3. 드롭 조회 — 상품명/가격/이미지/픽업일. 없으면 DROP_NOT_FOUND
        Drop drop = dropRepository.findById(item.getDropId())
                .orElseThrow(() -> new BusinessException(ErrorCode.DROP_NOT_FOUND));

        //가격은 int → BigDecimal 변환. 조회 시점 최신값이며 스냅샷이 아니다.
        BigDecimal price = BigDecimal.valueOf(drop.getDropProduct().getPrice());
        BigDecimal estimatedAmount = price.multiply(BigDecimal.valueOf(quantity));

        //픽업 가능일 — 지난 날짜는 제외하고 오름차순으로 내려준다.
        List<LocalDate> pickupDates = drop.getPickUpAvailableDate().stream()
                .filter(d -> !d.isBefore(now.toLocalDate()))
                .sorted()
                .toList();

        // 4. 판매자 상호명 조회. 없으면 null(방어).
        String sellerName = sellerRepository.findById(drop.getSellerId())
                .map(Seller::getBakeryName)
                .orElse(null);

        // 5. 만료까지 남은 초
        long remainingSeconds = Duration.between(now, cart.getExpiresAt()).getSeconds();

        // 6. 응답 조립
        return new CartDetailResult(
                cart.getCartId(),
                new CartDetailResult.DropInfo(
                        drop.getId(),
                        drop.getDropProduct().getName(),
                        price,
                        drop.getDropProduct().getImageUrl()
                ),
                new CartDetailResult.SellerInfo(
                        drop.getSellerId(),
                        sellerName
                ),
                quantity,
                estimatedAmount,
                pickupDates,
                cart.getPickupDate(),
                cart.getExpiresAt(),
                (int) remainingSeconds
        );
    }

    /**
     * memberId 로 카트 존재 여부만 확인한다. drop 쪽 방치된 재고 선점 회수 배치가
     * "카트가 실제로 만들어졌는지"를 판단할 때 쓴다.
     */
    @Transactional(readOnly = true)
    public boolean hasCart(Long memberId) {
        return cartRepository.existsByMemberId(memberId);
    }

    /**
     * 픽업 날짜 선택. 재선택 시 덮어쓴다.
     */
    @Transactional
    public CartPickupDateResult updatePickupDate(Long memberId, LocalDate pickupDate) {
        LocalDateTime now = LocalDateTime.now();

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

        // 4. 위조/stale 방어 — 요청된 날짜가 드롭의 픽업 가능일에 실제로 포함되는지 확인.
        //    화면 목록은 drop 에서 내려주지만, 요청 본문은 클라이언트가 만든 값이라 서버가 다시 확인한다.
        Drop drop = dropRepository.findById(cart.getItems().getDropId())
                .orElseThrow(() -> new BusinessException(ErrorCode.DROP_NOT_FOUND));
        if (!drop.getPickUpAvailableDate().contains(pickupDate)) {
            throw new BusinessException(ErrorCode.CART_INVALID_PICKUP_DATE);
        }

        // 5. 저장. 관리 상태 엔티티라 변경 감지로 커밋 시 자동 반영(별도 save 불필요).
        cart.updatePickupDate(pickupDate);

        return CartPickupDateResult.from(cart);
    }

    /**
     * 장바구니 삭제. 선점했던 재고를 drop 에서 복구시킨다(동기 호출).
     * 만료된 장바구니도 삭제 대상이므로 여기서는 CART_EXPIRED 를 던지지 않는다.
     *
     * (주의: 여기서 행을 지우면 만료 배치의 조회 범위에서 사라지므로,
     *  복구 요청을 반드시 삭제 전에 보내야 재고가 새지 않는다.)
     */
    @Transactional
    public void deleteCart(Long memberId) {
        Cart cart = cartRepository.findByMemberId(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CART_NOT_FOUND));

        //삭제 전에 재고 복구를 요청한다. 지우고 나면 dropId/quantity 를 읽을 수 없다.
        rollbackStockIfPresent(cart);

        //cart_items 는 cascade = ALL + orphanRemoval 이라 함께 삭제된다.
        cartRepository.delete(cart);
    }

    /**
     * 만료된 장바구니를 일괄 정리한다. 선점 재고를 복구시키고 삭제한다.
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
            //삭제 전에 재고 복구를 요청한다. 벌크 삭제를 쓰면 이 값을 잃어 복구를 못 시킨다.
            rollbackStockIfPresent(cart);
        }

        cartRepository.deleteAll(expiredCarts);
        return expiredCarts.size();
    }

    /**
     * 장바구니 이탈(삭제/만료/기존 정리) 시, 선점했던 재고를 drop 에 복구 요청한다(동기 호출).
     * drop 의 rollbackStock 이 entry 상태 변경 + 재고 복구를 수행한다. 재고 소유는 drop.
     */
    private void rollbackStockIfPresent(Cart cart) {
        CartItem item = cart.getItems();
        if (item != null) {
            dropLockService.rollbackStock(item.getDropId(), cart.getMemberId());
        }
    }
}
