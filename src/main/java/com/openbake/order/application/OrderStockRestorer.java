package com.openbake.order.application;

import com.openbake.order.application.port.ProductPort;
import com.openbake.order.application.port.ReservationPort;
import com.openbake.order.domain.Order;
import com.openbake.order.domain.OrderItem;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 재고 복구. <b>주체가 아니라 진입점이 갈린다</b> —
 * 드롭과 일반 상품은 결국 같은 product_inventories 행을 되돌린다.
 *
 * <pre>
 * 드롭      DropLockService.rollbackStock → productPort.rollbackQuantity → ProductInventory
 * 일반 상품  ProductService.rollbackStock                                → ProductInventory
 * </pre>
 *
 * 드롭 경로가 추가로 하는 일은 DropEntry 상태 전이와 선점 수량 조회뿐이다.
 * (DropInventory 계층이 따로 있지만 호출처가 0건인 죽은 코드다.)
 *
 * <b>복구는 두 번 불리면 안 된다.</b> ProductService.rollbackStock 이 총량은 넘지 않게
 * 막지만 "같은 주문에 대해 두 번"은 막지 못한다. 그래서 호출하는 쪽이 상태 전이로
 * 방어해야 한다 — 이미 종료된 주문을 다시 복구하지 않는다.
 */
@Component
@RequiredArgsConstructor
public class OrderStockRestorer {

    private final ProductPort productPort;
    private final ReservationPort reservationPort;

    /**
     * 결제 후 종료(취소·실패)에서의 복구. 재고가 확실히 깎여 있는 상태다.
     */
    public void restorePaid(Order order) {
        if (order.isDrop()) {
            restoreDropReservations(order);
            return;
        }
        //복구는 재고 벌크 UPDATE(clearAutomatically)라 첫 호출에 order 가 준영속이 된다.
        //반복 중 지연 로딩이 끊기지 않도록 필요한 값을 먼저 뽑아 둔다.
        List<StockLine> lines = order.getItems().stream()
                .map(item -> new StockLine(item.getProductId(), item.getQuantity()))
                .toList();

        for (StockLine line : lines) {
            productPort.rollbackStock(line.productId(), line.quantity());
        }
    }

    /**
     * 결제 전 종료(만료·사용자 취소)에서의 복구.
     *
     * <b>일반 상품은 되돌릴 것이 없다.</b> 재고 차감을 결제 성공 직후로 옮겼기 때문에
     * PENDING 동안 일반 상품 재고는 잡히지 않는다. 유령 재고가 생기지 않는다.
     *
     * 드롭은 사정이 다르다 — lock-start 에서 이미 깎였으므로 복구가 <b>필수</b>다.
     * 돌지 않으면 재고가 영구히 잠긴다.
     */
    public void restorePending(Order order) {
        if (order.isDrop()) {
            restoreDropReservations(order);
        }
    }

    private void restoreDropReservations(Order order) {
        Long memberId = order.getMemberId();
        //drop 복구도 결국 같은 product_inventories 벌크 UPDATE 를 타므로 먼저 뽑아 둔다.
        List<Long> dropIds = order.getItems().stream()
                .map(OrderItem::getDropId)
                .toList();

        for (Long dropId : dropIds) {
            //수량을 넘기지 않는다. drop 이 DropEntry.selectQuantity 를 읽어 되돌린다.
            reservationPort.rollbackStock(dropId, memberId);
        }
    }

    private record StockLine(Long productId, int quantity) {
    }
}
