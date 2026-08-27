package com.openbake.drop.domain.entity;

import com.openbake.common.exception.BusinessException;
import com.openbake.common.exception.ErrorCode;
import com.openbake.drop.domain.DropStatus;
import com.openbake.drop.domain.DropTimeSlot;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.Arrays;


@Entity
@Getter
@Table(name = "drops")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Drop {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DropStatus dropStatus; // 드롭 상태 (시작 전, 진행 중, 마감)

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(nullable = false)
    private int limitQuantity; // 1인당 한정 수량

    @Column(nullable = false)
    private LocalDateTime dropStart; // 드롭 시작 시간

    @Column(nullable = false)
    private LocalDateTime dropEnd; // 드롭 마감 시간

    // NULL이면 재고 확정 전. 조건부 UPDATE(markStockFinalized)로만 채워지며,
    // 드롭 종료 후에도 진행 중인 주문 만료 처리가 끝날 때까지 일부러 유예를 두고 채운다
    // (docs/11 관련 논의 — 너무 일찍 확정하면 뒤늦은 rollbackStock이 STOCK_NOT_INITIALIZED로 실패한다).
    @Column(name = "stock_finalized_at")
    private LocalDateTime stockFinalizedAt;

    @Builder
    public Drop(DropStatus dropStatus, Long productId, int limitQuantity, LocalDateTime dropStart, LocalDateTime dropEnd) {
        validateDropPeriod(dropStart, dropEnd);
        if (limitQuantity <= 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "1인당 제한 수량은 1개 이상이어야 합니다.");
        }
        if (productId == null || dropStatus == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "상품 정보, 드롭 상태는 필수입니다.");
        }


        this.dropStatus = dropStatus;
        this.productId = productId;
        this.limitQuantity = limitQuantity;
        this.dropStart = dropStart;
        this.dropEnd = dropEnd;
    }

    private void validateDropPeriod(LocalDateTime dropStart, LocalDateTime dropEnd) {
        if (dropStart == null || dropEnd == null) {
            throw new BusinessException(ErrorCode.INVALID_DROP_TIME, "시작 시간과 마감 시간은 필수입니다.");
        }
        if (!dropStart.isBefore(dropEnd)) {
            throw new BusinessException(ErrorCode.INVALID_DROP_TIME, "시작 시간은 마감 시간보다 이전이어야 합니다.");
        }
        if (dropStart.isBefore(LocalDateTime.now())) {
            throw new BusinessException(ErrorCode.INVALID_DROP_TIME, "드롭 시작 시간은 과거일 수 없습니다.");
        }
        boolean isValidSlot = Arrays.stream(DropTimeSlot.values())
                .anyMatch(dropTimeSlot -> dropTimeSlot.getStart().equals(dropStart.toLocalTime()));

        if (!isValidSlot) {
            throw new BusinessException(ErrorCode.TIMESLOT_NOT_CONTAINS);
        }
    }

    // 대기열 진입용 시간 검증 (재고 조회 없이 시간만 체크)
    public boolean isAccessible(LocalDateTime now) {
        // 이미 마감 처리된 드롭은 시각과 상관없이 거부
        if (this.dropStatus == DropStatus.COMPLETED) {
            return false;
        }
        // DB 상태가 UPCOMING이어도 실시간 시각이 시작과 종료 사이라면 통과
        return !now.isBefore(this.dropStart) && !now.isAfter(this.dropEnd);
    }

    // 시작 전(UPCOMING)인 드롭만 판매자가 수정/삭제할 수 있다 (시작 후에는 대기열/재고 선점 데이터와 어긋날 수 있음)
    public boolean isEditable() {
        return this.dropStatus == DropStatus.UPCOMING;
    }

    public void update(Long productId, int limitQuantity, LocalDateTime dropStart, LocalDateTime dropEnd) {
        validateDropPeriod(dropStart, dropEnd);

        if (limitQuantity <= 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "1인당 제한 수량은 1개 이상이어야 합니다.");
        }
        if (productId == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "상품 정보는 필수입니다.");
        }

        this.productId = productId;
        this.limitQuantity = limitQuantity;
        this.dropStart = dropStart;
        this.dropEnd = dropEnd;
    }
}