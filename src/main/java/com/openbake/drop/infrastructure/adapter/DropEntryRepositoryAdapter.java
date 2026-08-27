package com.openbake.drop.infrastructure.adapter;

import com.openbake.drop.domain.entity.DropEntry;
import com.openbake.drop.domain.repository.DropEntryRepository;
import com.openbake.drop.domain.EntryStatus;
import com.openbake.drop.infrastructure.jpa.DropEntryJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class DropEntryRepositoryAdapter implements DropEntryRepository {

    private final DropEntryJpaRepository dropEntryJpaRepository;
    @Override
    public Optional<DropEntry> findByDropIdAndMemberId(Long dropId, Long memberId) {
        return dropEntryJpaRepository.findByDropIdAndMemberId(dropId, memberId);
    }

    @Override
    public DropEntry save(DropEntry dropEntry) {
        return dropEntryJpaRepository.save(dropEntry);
    }

    @Override
    public int reserve(Long dropId, Long memberId, int selectQuantity) {
        return dropEntryJpaRepository.reserve(dropId, memberId, selectQuantity);
    }

    @Override
    public int fail(Long dropId, Long memberId) {
        return dropEntryJpaRepository.fail(dropId, memberId);
    }

    @Override
    public int complete(Long dropId, Long memberId) {
        return dropEntryJpaRepository.complete(dropId, memberId);
    }

    @Override
    public int expireEnteredEntries(Long dropId) {
        return dropEntryJpaRepository.expireEnteredEntries(dropId);
    }

    @Override
    public int sumReservedQuantity(Long dropId) {
        return dropEntryJpaRepository.sumReservedQuantity(dropId);
    }

    @Override
    public List<DropEntry> findExpiredReservations(LocalDateTime cutoff) {
        return dropEntryJpaRepository.findExpiredReservations(cutoff);
    }
}
