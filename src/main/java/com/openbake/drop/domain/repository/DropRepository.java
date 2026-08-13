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

}
