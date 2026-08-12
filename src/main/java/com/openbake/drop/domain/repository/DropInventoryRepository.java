package com.openbake.drop.domain.repository;

import com.openbake.drop.domain.entity.DropInventory;

public interface DropInventoryRepository {
    DropInventory save(DropInventory dropInventory);

    DropInventory findByDropId(Long dropId);

    void delete(DropInventory dropInventory);
}
