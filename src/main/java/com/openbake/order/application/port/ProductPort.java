package com.openbake.order.application.port;

import com.openbake.order.application.port.dto.ProductInfo;

import java.util.Optional;

/**
 * 일반 상품 조회·재고 포트.
 *
 * order 는 product 의 저장소/엔티티를 직접 보지 않고 이 인터페이스만 안다.
 * 지금 구현체는 같은 코어 안의 서비스를 호출하지만, product 가 분리되면
 * infrastructure 의 구현체만 FeignClient 로 갈아끼우면 된다.
 *
 * cart 에도 같은 이름의 포트가 있지만 필요한 계약이 다르다 — cart 는 조회만 하고,
 * order 는 차감·복구까지 한다. 도메인마다 자기 포트를 갖는 것이 이 구조의 전제다.
 */
public interface ProductPort {

    //상품이 삭제됐으면 빈 값. 소프트 삭제 상태는 구현체가 걸러낸다.
    Optional<ProductInfo> findProduct(Long productId);

    /**
     * 재고 차감. <b>결제가 성공한 뒤에</b> 호출한다.
     *
     * @return 차감했으면 true, 재고가 모자라 차감하지 못했으면 false
     *
     * false 는 예외가 아니라 정상적으로 일어나는 결과다 — 주문서를 쓰는 동안 남이
     * 먼저 사갔다는 뜻이고, 호출한 쪽은 이걸 받아 <b>환불로 되돌린다.</b>
     * 재고 확인 후 차감이 아니라 조건부 UPDATE 한 방이라 확인·차감 사이의 틈이 없다.
     */
    boolean decreaseStock(Long productId, int quantity);

    /**
     * 재고 복구. 취소·결제 실패 시.
     *
     * 두 번 불리면 재고가 부풀어 오버셀이 되므로, 호출하는 쪽이 주문 상태 전이로
     * 중복 호출을 막아야 한다. 이 메서드 자체는 총량을 넘지 않게만 방어한다.
     */
    void rollbackStock(Long productId, int quantity);
}
