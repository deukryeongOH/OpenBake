package com.openbake.drop.domain.repository;

import com.openbake.drop.domain.EntryStatus;
import com.openbake.drop.domain.entity.DropEntry;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface DropEntryRepository {
    Optional<DropEntry> findByDropIdAndMemberId(Long dropId, Long memberId);

    DropEntry save(DropEntry dropEntry);

    int reserve(Long dropId, Long memberId, int selectQuantity);

    // RESERVED/COMPLETED 둘 다 받아 원래 상태에 따라 FAILED/CANCELLED로 나눠 기록한다.
    int fail(Long dropId, Long memberId);

    // 결제 성공 시 선점 확정(RESERVED -> COMPLETED). docs/10 참고.
    int complete(Long dropId, Long memberId);

    // 마감된 드롭에서 ENTERED로 남은 진입 내역을 일괄 실패 처리
    int expireEnteredEntries(Long dropId);

    // Redis 재고 카운터 초기화·복구의 기준값
    int sumReservedQuantity(Long dropId);

    // 방치된 선점 후보(RESERVED 상태로 cutoff 이전에 선점된 것). docs/10 3.2절.
    List<DropEntry> findExpiredReservations(LocalDateTime cutoff);
}
