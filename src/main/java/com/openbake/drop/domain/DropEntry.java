package com.openbake.drop.domain;


import com.openbake.common.exception.BusinessException;
import com.openbake.common.exception.ErrorCode;
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
) // drop_id와 member_id 복합 유니크 제약조건 (로직 중복 검증에서 뚫리면 최후의 보루)
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
    private EntryStatus entryStatus; // 진입 내역

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
    public void completeReservation() { this.entryStatus = EntryStatus.RESERVED;}

    // 취소
    public void cancelEntry() {
        this.entryStatus = EntryStatus.CANCELLED;
    }

    public void completePayment(){
        this.entryStatus = EntryStatus.COMPLETED;
    }

    // 재고 부족으로 인한 주문 실패 또는 결제 실패
    public void failPaymentOrOrder(){
        this.entryStatus = EntryStatus.FAILED;
    }
}
