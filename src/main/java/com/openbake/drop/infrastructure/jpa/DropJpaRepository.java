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

    /*
     * 상태 전환은 모두 조건부 UPDATE다. "드롭당 정확히 1회"를 DB가 반환 행 수로 보장하게 해서
     * CachedDrop 의 tryMarkStarted/tryMarkEnded 플래그에 의존하지 않는다.
     * 그 플래그는 TodayDropCache.refresh() 때마다 초기화되므로(당일 드롭 등록/수정/삭제로도 트리거된다)
     * 무조건 UPDATE 하면 품절로 COMPLETED 가 된 드롭이 ACTIVE 로 되살아난다.
     */

    // 시작 전환: 아직 시작 전인 드롭만. 진행 중(ACTIVE)이나 품절(COMPLETED) 상태를 덮어쓰지 않는다.
    @Modifying(clearAutomatically = true)
    @Query("Update Drop d Set d.dropStatus = 'ACTIVE' WHERE d.id = :dropId AND d.dropStatus = 'UPCOMING'")
    int activeStatus(@Param("dropId") Long dropId);

    // 마감/품절 전환: 이미 COMPLETED 면 건드리지 않는다.
    // UPCOMING 도 대상에 포함해야 한다. 드롭 진행 시간 내내 서버가 내려가 있었다면 ACTIVE 를 거치지 않고 마감된다.
    @Modifying(clearAutomatically = true)
    @Query("Update Drop d Set d.dropStatus = 'COMPLETED' WHERE d.id = :dropId AND d.dropStatus <> 'COMPLETED'")
    int completeStatus(@Param("dropId") Long dropId);

    // 품절 복구: 품절된 드롭만 되살린다. 상태를 읽고 쓰는 대신 조건을 UPDATE 에 넣어 SELECT 를 없앤다.
    @Modifying(clearAutomatically = true)
    @Query("Update Drop d Set d.dropStatus = 'ACTIVE' WHERE d.id = :dropId AND d.dropStatus = 'COMPLETED'")
    int reviveFromSoldOut(@Param("dropId") Long dropId);

    // 재고 확정 후보: 확정 유예 시간이 지났는데 아직 확정 안 된 드롭.
    // TodayDropCache가 아니라 DB를 기준으로 돈다 — 자정을 넘겨 끝난 드롭은 캐시에서 이미 빠졌을 수 있다.
    @Query("select d from Drop d where d.stockFinalizedAt is null and d.dropEnd < :cutoff")
    List<Drop> findStockFinalizationCandidates(@Param("cutoff") LocalDateTime cutoff);

    // 재고 확정 표식: 드롭당 정확히 1회만 확정되도록 조건부로 막는다(activeStatus/completeStatus와 같은 패턴).
    @Modifying(clearAutomatically = true)
    @Query("Update Drop d Set d.stockFinalizedAt = :now WHERE d.id = :dropId AND d.stockFinalizedAt IS NULL")
    int markStockFinalized(@Param("dropId") Long dropId, @Param("now") LocalDateTime now);
}
