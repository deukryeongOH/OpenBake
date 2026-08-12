package com.openbake.drop.domain.entity;

import com.openbake.common.exception.BusinessException;
import com.openbake.common.exception.ErrorCode;
import com.openbake.drop.domain.DropProduct;
import com.openbake.drop.domain.DropStatus;
import com.openbake.drop.domain.DropTimeSlot;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;


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

    @Embedded
    private DropProduct dropProduct; // 드롭 상품 VO

    @Column(nullable = false)
    private int limitQuantity; // 1인당 한정 수량

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(
            name = "pickup_available_dates",
            joinColumns = @JoinColumn(name = "drop_id")
    )
    @Column(name = "available_date", nullable = false)
    private Set<LocalDate> pickUpAvailableDate = new HashSet<>(); // 픽업 가능 날짜

    @Column(nullable = false)
    private LocalDateTime dropStart; // 드롭 시작 시간

    @Column(nullable = false)
    private LocalDateTime dropEnd; // 드롭 마감 시간

    @Column(nullable = false)
    private Long sellerId; // 판매자 ID

    @Builder
    public Drop(DropStatus dropStatus, DropProduct dropProduct, Set<LocalDate> pickUpAvailableDates, int limitQuantity, LocalDateTime dropStart, LocalDateTime dropEnd, Long sellerId) {
        validateDropPeriod(dropStart, dropEnd);
        validatePickUpDates(dropEnd, pickUpAvailableDates);
        if (limitQuantity <= 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "1인당 제한 수량은 1개 이상이어야 합니다.");
        }
        if (sellerId == null || dropProduct == null || dropStatus == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "판매자 ID, 상품 정보, 드롭 상태는 필수입니다.");
        }


        this.dropStatus = dropStatus;
        this.dropProduct = dropProduct;
        this.limitQuantity = limitQuantity;
        this.dropStart = dropStart;
        this.dropEnd = dropEnd;
        this.sellerId = sellerId;

        if (pickUpAvailableDates != null) {
            this.pickUpAvailableDate.addAll(pickUpAvailableDates);
        }
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

    private void validatePickUpDates(LocalDateTime dropEnd, Set<LocalDate> pickUpAvailableDates) {
        if (pickUpAvailableDates == null || pickUpAvailableDates.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_PICKUP_DATE, "픽업 가능 날짜는 최소 하루 이상 필요합니다.");
        }

        LocalDate dropEndDate = dropEnd.toLocalDate();
        boolean hasInvalidDate = pickUpAvailableDates.stream()
                .anyMatch(date -> !date.isAfter(dropEndDate));

        if (hasInvalidDate) {
            throw new BusinessException(ErrorCode.INVALID_PICKUP_DATE); // 기본 메세지 사용
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

    public void changeStatus(DropStatus dropStatus) {
        this.dropStatus = dropStatus;
    }

    // 시작 전(UPCOMING)인 드롭만 판매자가 수정/삭제할 수 있다 (시작 후에는 대기열/재고 선점 데이터와 어긋날 수 있음)
    public boolean isEditable() {
        return this.dropStatus == DropStatus.UPCOMING;
    }

    public void update(DropProduct dropProduct, Set<LocalDate> pickUpAvailableDates, int limitQuantity, LocalDateTime dropStart, LocalDateTime dropEnd) {
        validateDropPeriod(dropStart, dropEnd);
        validatePickUpDates(dropEnd, pickUpAvailableDates);
        if (limitQuantity <= 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "1인당 제한 수량은 1개 이상이어야 합니다.");
        }
        if (dropProduct == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "상품 정보는 필수입니다.");
        }

        this.dropProduct = dropProduct;
        this.limitQuantity = limitQuantity;
        this.dropStart = dropStart;
        this.dropEnd = dropEnd;
        this.pickUpAvailableDate.clear();
        this.pickUpAvailableDate.addAll(pickUpAvailableDates);
    }
}