package com.openbake.product.domain;


import com.openbake.common.exception.BusinessException;
import com.openbake.common.exception.ErrorCode;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(name = "product_inventories")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductInventory {

    @Id
    @Column(name = "product_id")
    private Long productId;

    @Column(nullable = false)
    private int remainQuantity;

    @Column(nullable = false)
    private int totalQuantity;

    @Builder
    ProductInventory(int remainQuantity, int totalQuantity, Long productId) {
        if (productId == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "productID는 필수입니다.");
        }
        if (totalQuantity <= 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "총 수량은 0보다 커야 합니다.");
        }
        if (remainQuantity < 0 || remainQuantity > totalQuantity) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "잔여 수량은 0 이상, 총 수량 이하이어야 합니다.");
        }

        this.productId = productId;
        this.remainQuantity = remainQuantity;
        this.totalQuantity = totalQuantity;
    }

//    public void decreaseQuantity(int quantity) {
//        if (quantity <= 0) {
//            throw new BusinessException(ErrorCode.INVALID_INPUT, "수량은 1개 이상 선택해주세요.");
//        }
//        if (this.remainQuantity < quantity) {
//            throw new BusinessException(ErrorCode.PRODUCT_OUT_OF_STOCK);
//        }
//        this.remainQuantity -= quantity;
//    }

    // 판매자가 시작 전(UPCOMING) 드롭을 수정할 때 총 수량을 다시 잡는다.
    // UPCOMING 상태에서는 아직 아무도 재고를 선점할 수 없으므로(대기열 진입 자체가 dropStart 이후에만 가능) remainQuantity를 그냥 totalQuantity로 되돌려도 안전하다.
    public void resetQuantity(int totalQuantity) {
        if (totalQuantity <= 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "총 수량은 0보다 커야 합니다.");
        }
        this.totalQuantity = totalQuantity;
        this.remainQuantity = totalQuantity;
    }

    public void increaseStock(int quantity) {
        if (quantity <= 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "수량은 1개 이상 이어야 합니다.");
        }
        if (this.remainQuantity + quantity > this.totalQuantity) {
            throw new BusinessException(ErrorCode.INVALID_TOTAL_QUANTITY);
        }
        this.remainQuantity += quantity;

    }
}
