package com.openbake.drop.infrastructure.adapter;

import com.openbake.drop.domain.entity.DropInventory;
import com.openbake.drop.domain.repository.DropInventoryRepository;
import com.openbake.drop.infrastructure.jpa.DropInventoryJpaRepository;
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

    @Override
    public void delete(DropInventory dropInventory) {
        dropInventoryJpaRepository.delete(dropInventory);
    }
}
