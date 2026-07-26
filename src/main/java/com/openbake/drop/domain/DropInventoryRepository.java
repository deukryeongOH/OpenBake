package com.openbake.drop.domain;

public interface DropInventoryRepository {
    DropInventory save(DropInventory dropInventory);

    DropInventory findByDropId(Long dropId);
}
