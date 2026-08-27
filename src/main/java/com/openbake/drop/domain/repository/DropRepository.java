package com.openbake.drop.domain.repository;


import com.openbake.drop.domain.DropStatus;
import com.openbake.drop.domain.entity.Drop;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface DropRepository {
    Drop save(Drop drop);

    // 오늘 진행하는 드롭 리스트 반환
    List<Drop> findListByDropDate(LocalDate today);

    // dropId에 해당하는 드롭 반환
    Optional<Drop> findById(Long dropId);

    // 특정 기간 동안 특정 상태(UPCOMING/ACTIVE)인 드롭 목록 조회. dropStart 오름차순.
    List<Drop> findByDropStatusInAndDropStartBetweenOrderByDropStartAsc(
            List<DropStatus> dropStatuses,
            LocalDateTime from,
            LocalDateTime to
    );

    void delete(Drop drop);

    Drop findByProductId(Long aLong);

    // 상태 전환은 조건부 UPDATE이며, 실제로 전환된 행 수를 반환한다(0이면 이미 그 상태가 아니었다는 뜻).
    int activeStatus(Long dropId);

    int completeStatus(Long dropId);

    // 품절(COMPLETED)된 드롭만 ACTIVE로 되살린다
    int reviveFromSoldOut(Long dropId);

    // 재고 확정 유예 시간이 지났는데 아직 확정 안 된 드롭 목록
    List<Drop> findStockFinalizationCandidates(LocalDateTime cutoff);

    // 재고 확정 표식. 조건부 UPDATE라 실제로 확정했으면 1, 이미 확정돼 있었으면 0을 반환한다.
    int markStockFinalized(Long dropId, LocalDateTime now);
}
