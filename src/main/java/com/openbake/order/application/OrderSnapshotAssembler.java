package com.openbake.order.application;

import com.openbake.common.exception.BusinessException;
import com.openbake.common.exception.ErrorCode;
import com.openbake.order.application.port.MemberPort;
import com.openbake.order.application.port.SellerPort;
import com.openbake.order.application.port.dto.ProductInfo;
import com.openbake.order.application.port.dto.SellerInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

/**
 * 주문 생성 시 스냅샷 값을 모으고 주문 가능 여부를 검증하는 공통 부품.
 *
 * 세 경로가 조금씩 다른 검증을 하지만 <b>겹치는 부분이 대부분</b>이라 여기 모았다.
 * 검증은 전부 읽기만 하므로 주문 행을 만들기 전에 수행한다 —
 * 주문을 먼저 만들면 검증 실패를 표현할 상태가 더 필요해지고 쓰레기 행이 쌓인다.
 */
@Component
@RequiredArgsConstructor
public class OrderSnapshotAssembler {

    private final SellerPort sellerPort;
    private final MemberPort memberPort;

    public String buyerName(Long memberId) {
        return memberPort.getMember(memberId).data().name();
    }

    /**
     * 판매자 상호명 조회 캐시. 한 주문 안에서만 산다.
     *
     * 장바구니에 같은 가게 상품이 여럿 담기는 것이 흔해서, 없으면 항목 수만큼 조회가 나간다.
     */
    public SellerNameCache newSellerNameCache() {
        return new SellerNameCache(sellerPort);
    }

    /**
     * 일반 상품 주문 가능 여부.
     *
     * 재고 확인은 <b>안내용이다.</b> 확인과 실제 차감(결제 성공 직후) 사이에 남이 사가면
     * 그만이므로 보장이 아니다. 그래도 두는 이유는 차감이 결제 뒤로 밀리면서
     * "돈이 빠지고 나서야 품절을 아는" 경우가 생겼기 때문이다. 대부분은 여기서 걸린다.
     *
     * 품절(CA011)은 재고 부족과 사유가 달라 따로 남긴다 — 판매자가 내린 상품이지
     * 남이 먼저 사간 것이 아니다.
     */
    public void validateGeneralOrderable(ProductInfo product, int quantity, LocalDate pickUpDate) {
        if (product.soldOut()) {
            throw new BusinessException(ErrorCode.CART_PRODUCT_SOLD_OUT);
        }
        validatePickUpDate(product.pickUpAvailableDates().contains(pickUpDate), pickUpDate);

        if (product.remainQuantity() < quantity) {
            throw new BusinessException(ErrorCode.OUT_OF_STOCK);
        }
    }

    /**
     * 픽업일 검증.
     *
     * 장바구니에는 픽업일이 null 인 채로 담길 수 있다 — 필수 여부를 주문 단계에서 보는 것이
     * cart 와 합의된 계약이다(CA010 주석).
     */
    public void validatePickUpDate(boolean available, LocalDate pickUpDate) {
        if (pickUpDate == null) {
            throw new BusinessException(ErrorCode.PICKUP_DATE_NOT_SELECTED);
        }
        if (pickUpDate.isBefore(LocalDate.now())) {
            throw new BusinessException(ErrorCode.CART_PICKUP_DATE_UNAVAILABLE);
        }
        if (!available) {
            throw new BusinessException(ErrorCode.CART_INVALID_PICKUP_DATE);
        }
    }

    @RequiredArgsConstructor
    public static class SellerNameCache {
        private final SellerPort sellerPort;
        private final Map<Long, String> cache = new HashMap<>();

        /**
         * 판매자를 못 찾으면 null. 상호명은 표시용이라 주문 자체를 실패시키지 않는다.
         *
         * sellers 는 삭제 경로가 없어 사실상 일어나지 않지만, 표시값 때문에 결제가
         * 막히는 것이 더 나쁘다.
         */
        public String get(Long sellerId) {
            return cache.computeIfAbsent(sellerId, id -> sellerPort.findSeller(id)
                    .map(SellerInfo::sellerName)
                    .orElse(null));
        }
    }
}
