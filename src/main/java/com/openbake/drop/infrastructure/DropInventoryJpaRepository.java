package com.openbake.drop.infrastructure;

import com.openbake.drop.domain.DropInventory;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DropInventoryJpaRepository extends JpaRepository<DropInventory, Long> {
    DropInventory findByDropId(Long dropId);
}
