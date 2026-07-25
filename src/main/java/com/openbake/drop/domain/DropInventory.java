package com.openbake.drop.domain;


import com.openbake.common.exception.BusinessException;
import com.openbake.common.exception.ErrorCode;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(name = "drop_inventories")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DropInventory {

    @Id
    @Column(name = "drop_id")
    private Long dropId; // dropId를 프라이머리 키로 직접 사용

    @Column(nullable = false)
    private int totalQuantity; // 총 발매 수량 (한정 수량)

    @Column(nullable = false)
    private int remainQuantity; // 남은 재고 수량

    @Version
    private Long version; // 2차 방어선 (분산 락에서 실패 시 낙관적 락 적용위해)

    @Builder
    public DropInventory(int totalQuantity, int remainQuantity, Long dropId) {
        if (dropId == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "Drop ID는 필수입니다.");
        }
        if (totalQuantity <= 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "총 수량은 0보다 커야 합니다.");
        }
        if (remainQuantity < 0 || remainQuantity > totalQuantity) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "잔여 수량은 0 이상, 총 수량 이하이어야 합니다.");
        }

        this.totalQuantity = totalQuantity;
        this.remainQuantity = remainQuantity;
        this.dropId = dropId;
    }

    public void decreaseQuantity(int quantity) {
        if (quantity <= 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "수량은 1개 이상 선택해주세요.");
        }
        if (this.remainQuantity < quantity) {
            throw new BusinessException(ErrorCode.DROP_OUT_OF_STOCK);
        }
        this.remainQuantity -= quantity;
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
