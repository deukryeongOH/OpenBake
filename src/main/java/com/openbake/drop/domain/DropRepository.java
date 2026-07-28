package com.openbake.drop.domain;


import java.time.LocalDateTime;
import java.util.List;
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

    // 수정 시 "하루 1개 제한" 재검증용. 수정 대상 드롭 자신은 제외하고 확인한다.
    boolean existsByDropStartBetweenAndIdNot(LocalDateTime startOfDay, LocalDateTime endOfDay, Long excludeDropId);

    boolean existsBySellerIdAndDropStartBetweenAndIdNot(Long sellerId, LocalDateTime startOfDay, LocalDateTime endOfDay, Long excludeDropId);

    // 판매자 본인이 등록한 드롭 목록 조회
    List<Drop> findAllBySellerId(Long sellerId);

    void delete(Drop drop);
}
