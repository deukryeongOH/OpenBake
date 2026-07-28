package com.openbake.drop.infrastructure;

import com.openbake.drop.domain.Drop;
import com.openbake.drop.domain.DropRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
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

    @Override // 해당 판매자가 해당 날짜(00:00:00 ~ 23:59:59)에 이미 등록한 드롭이 있는지 확인
    public boolean existsBySellerIdAndDropStartBetween(Long sellerId, LocalDateTime startOfDay, LocalDateTime endOfDay) {
        return dropJpaRepository.existsBySellerIdAndDropStartBetween(sellerId, startOfDay, endOfDay);
    }

    @Override // 오늘 진행 할 드롭 확인
    public Optional<Drop> findByDropStartBetween(LocalDateTime todayStart, LocalDateTime todayEnd) {
        return dropJpaRepository.findByDropStartBetween(todayStart, todayEnd);
    }

    @Override
    public Optional<Drop> findById(Long dropId) {
        return dropJpaRepository.findById(dropId);
    }

    @Override
    public Boolean existsByDropStartBetween(LocalDateTime startOfDay, LocalDateTime endOfDay) {
        return dropJpaRepository.existsByDropStartBetween(startOfDay, endOfDay);
    }

    @Override
    public boolean existsByDropStartBetweenAndIdNot(LocalDateTime startOfDay, LocalDateTime endOfDay, Long excludeDropId) {
        return dropJpaRepository.existsByDropStartBetweenAndIdNot(startOfDay, endOfDay, excludeDropId);
    }

    @Override
    public boolean existsBySellerIdAndDropStartBetweenAndIdNot(Long sellerId, LocalDateTime startOfDay, LocalDateTime endOfDay, Long excludeDropId) {
        return dropJpaRepository.existsBySellerIdAndDropStartBetweenAndIdNot(sellerId, startOfDay, endOfDay, excludeDropId);
    }

    @Override
    public List<Drop> findAllBySellerId(Long sellerId) {
        return dropJpaRepository.findAllBySellerId(sellerId);
    }

    @Override
    public void delete(Drop drop) {
        dropJpaRepository.delete(drop);
    }
}
