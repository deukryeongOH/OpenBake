package com.openbake.drop.infrastructure;

import com.openbake.drop.domain.DropInventory;
import com.openbake.drop.domain.DropInventoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class DropInventoryRepositoryAdapter implements DropInventoryRepository {
    private final DropInventoryJpaRepository dropInventoryJpaRepository;

    @Override
    public DropInventory save(DropInventory dropInventory) {
        return dropInventoryJpaRepository.save(dropInventory);
    }

    @Override
    public DropInventory findByDropId(Long dropId) {
        return dropInventoryJpaRepository.findByDropId(dropId);
    }
}
