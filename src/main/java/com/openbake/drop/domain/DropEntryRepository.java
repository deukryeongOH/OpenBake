package com.openbake.drop.domain;

import java.util.List;
import java.util.Optional;

public interface DropEntryRepository {
    // 현재 입장 중이거나 재고를 선점한 상태인지 확인
    boolean existsByDropIdAndMemberIdAndEntryStatusIn(Long dropId, Long memberId, List<EntryStatus> statuss);

    Optional<DropEntry> findByDropIdAndMemberId(Long dropId, Long memberId);

    DropEntry save(DropEntry dropEntry);
}
