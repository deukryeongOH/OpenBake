package com.openbake.drop.infrastructure;

import com.openbake.drop.domain.DropEntry;
import com.openbake.drop.domain.EntryStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DropEntryJpaRepository extends JpaRepository<DropEntry, Long> {

    // 현재 입장 중이거나 재고를 선점한 상태인지 확인
    boolean existsByDropIdAndMemberIdAndEntryStatusIn(Long dropId, Long memberId, List<EntryStatus> statuss);
    Optional<DropEntry> findByDropIdAndMemberId(Long dropId, Long memberId);
}
