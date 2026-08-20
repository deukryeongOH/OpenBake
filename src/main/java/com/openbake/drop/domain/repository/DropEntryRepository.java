package com.openbake.drop.domain.repository;

import com.openbake.drop.domain.EntryStatus;
import com.openbake.drop.domain.entity.DropEntry;

import java.util.List;
import java.util.Optional;

public interface DropEntryRepository {
    // 현재 입장 중이거나 재고를 선점한 상태인지 확인
    boolean existsByDropIdAndMemberIdAndEntryStatusIn(Long dropId, Long memberId, List<EntryStatus> statuss);

    Optional<DropEntry> findByDropIdAndMemberId(Long dropId, Long memberId);

    DropEntry save(DropEntry dropEntry);

    // 재입장 여부 판단
    boolean existsByDropIdAndMemberId(Long dropId, Long memberId);

    int reserve(Long dropId, Long memberId, int selectQuantity);

    int fail(Long dropId, Long memberId);

    // Redis 재고 카운터 초기화·복구의 기준값
    int sumReservedQuantity(Long dropId);
}
