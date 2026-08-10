package com.openbake.drop.infrastructure.adapter;

import com.openbake.drop.domain.entity.DropEntry;
import com.openbake.drop.domain.repository.DropEntryRepository;
import com.openbake.drop.domain.EntryStatus;
import com.openbake.drop.infrastructure.jpa.DropEntryJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class DropEntryRepositoryAdapter implements DropEntryRepository {

    private final DropEntryJpaRepository dropEntryJpaRepository;
    @Override
    public boolean existsByDropIdAndMemberIdAndEntryStatusIn(Long dropId, Long memberId, List<EntryStatus> statuses) {
        return dropEntryJpaRepository.existsByDropIdAndMemberIdAndEntryStatusIn(dropId, memberId, statuses);
    }

    @Override
    public Optional<DropEntry> findByDropIdAndMemberId(Long dropId, Long memberId) {
        return dropEntryJpaRepository.findByDropIdAndMemberId(dropId, memberId);
    }

    @Override
    public DropEntry save(DropEntry dropEntry) {
        return dropEntryJpaRepository.save(dropEntry);
    }

    @Override
    public boolean existsByDropIdAndMemberId(Long dropId, Long memberId) {
        return dropEntryJpaRepository.existsByDropIdAndMemberId(dropId, memberId);
    }

}
