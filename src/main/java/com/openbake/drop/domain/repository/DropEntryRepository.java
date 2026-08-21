package com.openbake.drop.domain.repository;

import com.openbake.drop.domain.EntryStatus;
import com.openbake.drop.domain.entity.DropEntry;

import java.util.List;
import java.util.Optional;

public interface DropEntryRepository {
    Optional<DropEntry> findByDropIdAndMemberId(Long dropId, Long memberId);

    DropEntry save(DropEntry dropEntry);

    int reserve(Long dropId, Long memberId, int selectQuantity);

    int fail(Long dropId, Long memberId);

    // 마감된 드롭에서 ENTERED로 남은 진입 내역을 일괄 실패 처리
    int expireEnteredEntries(Long dropId);

    // Redis 재고 카운터 초기화·복구의 기준값
    int sumReservedQuantity(Long dropId);
}
