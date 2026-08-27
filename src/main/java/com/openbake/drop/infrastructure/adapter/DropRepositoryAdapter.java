package com.openbake.drop.infrastructure.adapter;

import com.openbake.common.exception.BusinessException;
import com.openbake.common.exception.ErrorCode;
import com.openbake.drop.domain.entity.Drop;
import com.openbake.drop.domain.repository.DropRepository;
import com.openbake.drop.domain.DropStatus;
import com.openbake.drop.infrastructure.jpa.DropJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class DropRepositoryAdapter implements DropRepository {
    private final DropJpaRepository dropJpaRepository;

    @Override
    public Drop save(Drop drop) {
        return dropJpaRepository.save(drop);
    }


    @Override // 해당 날짜에 등록된 드롭 리스트 반환
    public List<Drop> findListByDropDate(LocalDate today) {
        LocalDateTime startOfDay = today.atStartOfDay();
        LocalDateTime endOfDay = today.atTime(LocalTime.MAX);
        return dropJpaRepository.findAllByDropStartBetween(startOfDay, endOfDay);
    }

    @Override
    public Optional<Drop> findById(Long dropId) {
        return dropJpaRepository.findById(dropId);
    }

    @Override
    public List<Drop> findByDropStatusInAndDropStartBetweenOrderByDropStartAsc(
            List<DropStatus> dropStatuses,
            LocalDateTime from,
            LocalDateTime to
    ) {
        return dropJpaRepository
                .findByDropStatusInAndDropStartBetweenOrderByDropStartAsc(
                        dropStatuses,
                        from,
                        to
                );
    }

    @Override
    public void delete(Drop drop) {
        dropJpaRepository.delete(drop);
    }

    @Override
    public Drop findByProductId(Long productId) {
        return dropJpaRepository.findByProductId(productId).orElseThrow(() -> new BusinessException(ErrorCode.DROP_NOT_FOUND));
    }

    @Override
    public int activeStatus(Long dropId) {
        return dropJpaRepository.activeStatus(dropId);
    }

    @Override
    public int completeStatus(Long dropId) {
        return dropJpaRepository.completeStatus(dropId);
    }

    @Override
    public int reviveFromSoldOut(Long dropId) {
        return dropJpaRepository.reviveFromSoldOut(dropId);
    }

    @Override
    public List<Drop> findStockFinalizationCandidates(LocalDateTime cutoff) {
        return dropJpaRepository.findStockFinalizationCandidates(cutoff);
    }

    @Override
    public int markStockFinalized(Long dropId, LocalDateTime now) {
        return dropJpaRepository.markStockFinalized(dropId, now);
    }
}
