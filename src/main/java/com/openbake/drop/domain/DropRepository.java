package com.openbake.drop.domain;


import java.time.LocalDateTime;
import java.util.Optional;

public interface DropRepository {
    Drop save(Drop drop);

    // 해당 판매자가 해당 날짜(00:00:00 ~ 23:59:59)에 이미 등록한 드롭이 있는지 확인
    boolean existsBySellerIdAndDropStartBetween(Long sellerId, LocalDateTime startOfDay, LocalDateTime endOfDay);

    // 오늘 진행하는 드롭 반환
    Optional<Drop> findByDropStartBetween(LocalDateTime todayStart, LocalDateTime todayEnd);

    // dropId에 해당하는 드롭 반환
    Optional<Drop> findById(Long dropId);

    Boolean existsByDropStartBetween(LocalDateTime startOfDay, LocalDateTime endOfDay);
}
