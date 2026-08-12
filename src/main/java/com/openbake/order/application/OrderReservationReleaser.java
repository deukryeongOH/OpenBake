package com.openbake.order.application;

import com.openbake.cart.domain.CartItem;
import com.openbake.cart.domain.CartRepository;
import com.openbake.drop.application.service.DropLockService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 주문 생성 중 결제가 실패했을 때의 보상 처리.
 * 담기 시점에 drop 이 선점한 재고를 복구하고, 장바구니를 정리한다.
 *
 * 주문 생성 트랜잭션은 결제 예외로 롤백되지만 이 보상은 커밋돼야 하므로
 * REQUIRES_NEW 로 별도 트랜잭션에서 수행한다.
 */
@Component
@RequiredArgsConstructor
public class OrderReservationReleaser {

    private final CartRepository cartRepository;
    private final DropLockService dropLockService;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void releaseOnPaymentFailure(Long memberId) {
        cartRepository.findByMemberId(memberId).ifPresent(cart -> {
            CartItem item = cart.getItems();
            if (item != null) {
                //재고 복구는 drop 소유라 rollbackStock 을 호출한다(직접 되돌리지 않는다).
                dropLockService.rollbackStock(item.getDropId(), memberId);
            }
            //결제 실패한 장바구니는 더 못 쓰게 정리한다.
            cartRepository.delete(cart);
        });
    }
}
