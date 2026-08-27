package com.openbake.drop.infrastructure.jpa;

import com.openbake.drop.domain.entity.DropEntry;
import com.openbake.drop.domain.EntryStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface DropEntryJpaRepository extends JpaRepository<DropEntry, Long> {

    Optional<DropEntry> findByDropIdAndMemberId(Long dropId, Long memberId);

    // reservedAt은 애플리케이션 시각이 아니라 DB 시각(CURRENT_TIMESTAMP)으로 남긴다 —
    // 인스턴스가 여러 대라 서버 시계가 조금씩 다를 수 있고, 이 값이 만료 스위퍼의
    // 기준이라 클럭 스큐가 그대로 회수 시점의 오차가 된다.
    @Modifying(clearAutomatically = true)
    @Query("Update DropEntry e SET e.entryStatus = 'RESERVED', e.selectQuantity = :selectQuantity, e.reservedAt = CURRENT_TIMESTAMP "
            + "WHERE e.dropId = :dropId AND e.memberId = :memberId AND e.entryStatus = 'ENTERED'")
    int reserve(@Param("dropId") Long dropId,@Param("memberId") Long memberId, @Param("selectQuantity") int selectQuantity);

    /**
     * 재고 롤백. RESERVED(결제 전 취소·만료)와 COMPLETED(결제 후 취소) 둘 다 받는다.
     *
     * 원래 상태에 따라 도착 상태가 갈린다 — RESERVED였으면 FAILED(응모가 결제로 이어지지
     * 못함), COMPLETED였으면 CANCELLED(결제까지 끝난 걸 나중에 취소, 재고는 복구됨)로
     * 정확히 구분해 기록한다. EntryStatus.CANCELLED가 정의만 되고 안 쓰이던 것을 여기서
     * 실제로 채운다(docs/10 3.1절).
     *
     * COMPLETED를 받아주지 않으면, 결제 완료 후 취소된 드롭 주문의 재고 복구가
     * NOT_RESERVED_STATUS로 실패한다 — completeReservation이 RESERVED를 COMPLETED로
     * 옮긴 뒤에는 이 조건이 더 이상 걸리지 않기 때문이다.
     */
    @Modifying(clearAutomatically = true)
    @Query("Update DropEntry e SET e.entryStatus = "
            + "CASE WHEN e.entryStatus = 'COMPLETED' THEN 'CANCELLED' ELSE 'FAILED' END "
            + "WHERE e.dropId = :dropId AND e.memberId = :memberId "
            + "AND e.entryStatus IN ('RESERVED', 'COMPLETED')")
    int fail(@Param("dropId") Long dropId,@Param("memberId") Long memberId);

    /**
     * 결제 성공 시 선점을 확정한다(RESERVED -> COMPLETED). docs/10 3.1절 1단계.
     *
     * 결제 성공 트랜잭션 안에서 호출되므로 호출자는 반환값 0을 예외로 던지지 않고
     * 로그만 남겨야 한다 — 여기서 예외가 나면 이미 끝난 결제(markPaid)까지 롤백된다.
     */
    @Modifying(clearAutomatically = true)
    @Query("Update DropEntry e SET e.entryStatus = 'COMPLETED' " + "WHERE e.dropId = :dropId AND e.memberId = :memberId" +
            " AND e.entryStatus = 'RESERVED'")
    int complete(@Param("dropId") Long dropId,@Param("memberId") Long memberId);

    // 마감된 드롭에서 ENTERED로 남은 진입 내역 정리. 드롭당 UPDATE 1회라 경합이 없다.
    @Modifying(clearAutomatically = true)
    @Query("Update DropEntry e SET e.entryStatus = 'FAILED' " +
            "WHERE e.dropId = :dropId AND e.entryStatus = 'ENTERED'")
    int expireEnteredEntries(@Param("dropId") Long dropId);

    // Redis 재고 카운터의 초기값 계산용. 드롭 시작 시점(합계 0)과 카운터 유실 후 복구 시점 모두
    // 같은 식으로 정확한 잔여 수량을 얻기 위해 선점 합계를 집계한다.
    //
    // RESERVED뿐 아니라 COMPLETED도 더한다 — 결제까지 끝난 항목도 재고가 나간 것이므로
    // 빼야 한다. COMPLETED를 빼면 결제 완료 직후부터 이 합계가 실제보다 작게 나와
    // checkDrift가 정상 주문마다 거짓 경보를 울린다.
    @Query("SELECT COALESCE(SUM(e.selectQuantity), 0) FROM DropEntry e " +
            "WHERE e.dropId = :dropId AND e.entryStatus IN ('RESERVED', 'COMPLETED')")
    int sumReservedQuantity(@Param("dropId") Long dropId);

    /**
     * 방치된 선점 후보. TodayDropCache가 아니라 DB를 기준으로 돈다 — dropEnd가 자정을
     * 넘긴 드롭의 방치된 선점도 캐시 경계와 무관하게 잡아야 한다(docs/12번 문서의
     * finalizeStockAfterGracePeriod와 같은 이유). COMPLETED는 대상이 아니다 — 1단계
     * 덕분에 결제 완료 건은 여기 걸리지 않는다.
     */
    @Query("SELECT e FROM DropEntry e WHERE e.entryStatus = 'RESERVED' AND e.reservedAt < :cutoff")
    List<DropEntry> findExpiredReservations(@Param("cutoff") LocalDateTime cutoff);
}
