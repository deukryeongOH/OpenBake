package com.openbake.drop.infrastructure;

import com.openbake.drop.domain.Drop;
import com.openbake.drop.domain.DropStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface DropJpaRepository extends JpaRepository<Drop, Long> {
    // 해당 판매자가 해당 날짜(00:00:00 ~ 23:59:59)에 이미 등록한 드롭이 있는지 확인
    boolean existsBySellerIdAndDropStartBetween(Long sellerId, LocalDateTime startOfDay, LocalDateTime endOfDay);

    // 해당 날짜(00:00:00 ~ 23:59:59)에 등록된 드롭 목록 조회
    List<Drop> findAllByDropStartBetween(LocalDateTime startOfDay, LocalDateTime endOfDay);

    // 오늘 진행 예정인 드롭 확인
    Optional<Drop> findByDropStartBetween(LocalDateTime todayStart, LocalDateTime todayEnd);

    Boolean existsByDropStartBetween(LocalDateTime startOfDay, LocalDateTime endOfDay);

    boolean existsByDropStartBetweenAndIdNot(LocalDateTime startOfDay, LocalDateTime endOfDay, Long excludeDropId);

    boolean existsBySellerIdAndDropStartBetweenAndIdNot(Long sellerId, LocalDateTime startOfDay, LocalDateTime endOfDay, Long excludeDropId);

    List<Drop> findAllBySellerId(Long sellerId);

    List<Drop> findByDropStatusInAndDropStartBetweenOrderByDropStartAsc(
            List<DropStatus> dropStatuses,
            LocalDateTime from,
            LocalDateTime to
    );
}
