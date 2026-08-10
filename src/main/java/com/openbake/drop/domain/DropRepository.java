package com.openbake.drop.domain;


import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface DropRepository {
    Drop save(Drop drop);

    // 해당 판매자가 해당 날짜(00:00:00 ~ 23:59:59)에 이미 등록한 드롭이 있는지 확인
    boolean existsBySellerIdAndDropStartBetween(Long sellerId, LocalDateTime startOfDay, LocalDateTime endOfDay);

    // 오늘 진행하는 드롭 리스트 반환
    List<Drop> findListByDropDate(LocalDate today);

    // dropId에 해당하는 드롭 반환
    Optional<Drop> findById(Long dropId);

    boolean existsBySellerIdAndDropStartBetweenAndIdNot(Long sellerId, LocalDateTime startOfDay, LocalDateTime endOfDay, Long excludeDropId);

    // 판매자 본인이 등록한 드롭 목록 조회
    List<Drop> findAllBySellerId(Long sellerId);

    // 특정 기간 동안 특정 상태(UPCOMING/ACTIVE)인 드롭 목록 조회. dropStart 오름차순.
    List<Drop> findByDropStatusInAndDropStartBetweenOrderByDropStartAsc(
            List<DropStatus> dropStatuses,
            LocalDateTime from,
            LocalDateTime to
    );

    void delete(Drop drop);
}
