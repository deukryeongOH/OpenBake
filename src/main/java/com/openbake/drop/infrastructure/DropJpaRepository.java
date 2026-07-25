package com.openbake.drop.infrastructure;

import com.openbake.drop.domain.Drop;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface DropJpaRepository extends JpaRepository<Drop, Long> {
    // 해당 판매자가 해당 날짜(00:00:00 ~ 23:59:59)에 이미 등록한 드롭이 있는지 확인
    boolean existsBySellerIdAndDropStartBetween(Long sellerId, LocalDateTime startOfDay, LocalDateTime endOfDay);

    // 오늘 진행 예정인 드롭 확인
    Optional<Drop> findByDropStartBetween(LocalDateTime todayStart, LocalDateTime todayEnd);

    Boolean existsByDropStartBetween(LocalDateTime startOfDay, LocalDateTime endOfDay);
}
