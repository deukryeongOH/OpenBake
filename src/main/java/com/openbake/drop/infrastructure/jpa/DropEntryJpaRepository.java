package com.openbake.drop.infrastructure.jpa;

import com.openbake.drop.domain.entity.DropEntry;
import com.openbake.drop.domain.EntryStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface DropEntryJpaRepository extends JpaRepository<DropEntry, Long> {

    Optional<DropEntry> findByDropIdAndMemberId(Long dropId, Long memberId);

    @Modifying(clearAutomatically = true)
    @Query("Update DropEntry e SET e.entryStatus = 'RESERVED', e.selectQuantity = :selectQuantity " + "WHERE e.dropId = :dropId AND e.memberId = :memberId" +
            " AND e.entryStatus = 'ENTERED'")
    int reserve(@Param("dropId") Long dropId,@Param("memberId") Long memberId, @Param("selectQuantity") int selectQuantity);

    @Modifying(clearAutomatically = true)
    @Query("Update DropEntry e SET e.entryStatus = 'FAILED' " + "WHERE e.dropId = :dropId AND e.memberId = :memberId" +
            " AND e.entryStatus = 'RESERVED'")
    int fail(@Param("dropId") Long dropId,@Param("memberId") Long memberId);

    // 마감된 드롭에서 ENTERED로 남은 진입 내역 정리. 드롭당 UPDATE 1회라 경합이 없다.
    @Modifying(clearAutomatically = true)
    @Query("Update DropEntry e SET e.entryStatus = 'FAILED' " +
            "WHERE e.dropId = :dropId AND e.entryStatus = 'ENTERED'")
    int expireEnteredEntries(@Param("dropId") Long dropId);

    // Redis 재고 카운터의 초기값 계산용. 드롭 시작 시점(합계 0)과 카운터 유실 후 복구 시점 모두
    // 같은 식으로 정확한 잔여 수량을 얻기 위해 선점 합계를 집계한다.
    @Query("SELECT COALESCE(SUM(e.selectQuantity), 0) FROM DropEntry e " +
            "WHERE e.dropId = :dropId AND e.entryStatus = 'RESERVED'")
    int sumReservedQuantity(@Param("dropId") Long dropId);
}
