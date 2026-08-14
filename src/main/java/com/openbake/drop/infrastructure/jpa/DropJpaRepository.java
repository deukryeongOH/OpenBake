package com.openbake.drop.infrastructure.jpa;

import com.openbake.drop.domain.entity.Drop;
import com.openbake.drop.domain.DropStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface DropJpaRepository extends JpaRepository<Drop, Long> {
    // 해당 날짜(00:00:00 ~ 23:59:59)에 등록된 드롭 목록 조회
    List<Drop> findAllByDropStartBetween(LocalDateTime startOfDay, LocalDateTime endOfDay);

    List<Drop> findByDropStatusInAndDropStartBetweenOrderByDropStartAsc(
            List<DropStatus> dropStatuses,
            LocalDateTime from,
            LocalDateTime to
    );

    Optional<Drop> findByProductId(Long productId);

    @Modifying(clearAutomatically = true)
    @Query("Update Drop d Set d.dropStatus = 'ACTIVE' WHERE d.id = :dropId")
    void activeStatus(@Param("dropId") Long dropId);

    @Modifying(clearAutomatically = true)
    @Query("Update Drop d Set d.dropStatus = 'COMPLETED' WHERE d.id = :dropId")
    void completeStatus(Long dropId);
}
