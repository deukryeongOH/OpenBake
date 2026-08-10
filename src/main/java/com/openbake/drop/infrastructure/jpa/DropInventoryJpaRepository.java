package com.openbake.drop.infrastructure.jpa;

import com.openbake.drop.domain.entity.DropInventory;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DropInventoryJpaRepository extends JpaRepository<DropInventory, Long> {
    DropInventory findByDropId(Long dropId);
}
