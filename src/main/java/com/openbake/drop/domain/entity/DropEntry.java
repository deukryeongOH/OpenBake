package com.openbake.drop.domain.entity;


import com.openbake.common.exception.BusinessException;
import com.openbake.common.exception.ErrorCode;
import com.openbake.drop.domain.EntryStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Getter
@Table(
        name = "drop_entries",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_drop_member",
                        columnNames = {"drop_id", "member_id"}
                )
        },indexes = {
        @Index(name = "idx_member_entry_time", columnList = "member_id, entry_time DESC")
        }
) // drop_id와 member_id 복합 유니크 제약조건 (confirm-entry 빠르게 2번 클릭 시 락이 걸리지 않으므로 유니크 제약으로 불필요한 상태 값 접근 제한)
@EntityListeners(AuditingEntityListener.class) // JPA Auditing 적용
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DropEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long dropEntryId;

    @Column(name = "drop_id", nullable = false)
    private Long dropId;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private EntryStatus entryStatus; // 진입 내역 (최초 실패 시각)

    @Column(nullable = false)
    private int selectQuantity; // 사용자가 선택한 수량

    @CreatedDate // JPA (엔티티 저장 시점에 JPA가 자동으로 시간 주입)
    @Column(nullable = false, updatable = false)
    private LocalDateTime entryTime; // 진입 시간 (선착순 판정의 기준)

    @Builder
    public DropEntry(Long dropId, Long memberId, EntryStatus entryStatus) {
        if (dropId == null || memberId == null || entryStatus == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "드롭 ID, 회원 ID, 진입 상태는 필수입니다.");
        }
        this.dropId = dropId;
        this.memberId = memberId;
        this.entryStatus = entryStatus;
    }

    // 대기열을 통과해서 최초로 생성될 때의 정적 팩토리 메서드
    public static DropEntry createInitialEntry(Long dropId, Long memberId) {
        return DropEntry.builder()
                .dropId(dropId)
                .memberId(memberId)
                .entryStatus(EntryStatus.ENTERED) // 최초 상태: ENTERED
                .build();
    }

    // 주문/재고 선점 성공 시 상태 변경
    public void reserveEntryAndSaveSelectQuantity(int selectQuantity) {
        this.entryStatus = EntryStatus.RESERVED;
        this.selectQuantity = selectQuantity;
    }

    // 주문 취소 or 장바구니 만료
    public void cancelEntry() {
        this.entryStatus = EntryStatus.CANCELLED;
    }

    // 결제 완료
    public void completeEntry(){
        this.entryStatus = EntryStatus.COMPLETED;
    }

    // 재고 부족으로 인한 주문 실패 또는 결제 실패
    public void failEntry(){
        this.entryStatus = EntryStatus.FAILED;
    }

    public void changeStatusEnter() {
        this.entryStatus = EntryStatus.ENTERED;
    }
}
