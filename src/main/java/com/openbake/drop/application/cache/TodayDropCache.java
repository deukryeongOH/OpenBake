package com.openbake.drop.application.cache;

import com.openbake.drop.application.port.ProductPort;
import com.openbake.drop.domain.repository.DropRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

// 오늘 진행되는 드롭 정보를 캐싱한다. DropScheduler가 매초 DB를 조회하지 않도록
// 자정/기동 시에 refresh 하며, 당일 드롭이 등록/수정/삭제될 때도 DropService가 refresh를 호출해
// 캐시가 자정 시점 스냅샷으로 굳어버리는 것을 막는다.
@Component
@RequiredArgsConstructor
public class TodayDropCache {

    private final DropRepository dropRepository;
    private final ProductPort productPort;

    private final AtomicReference<List<CachedDrop>> cachedDrop = new AtomicReference<>(List.of());

    /**
     * 드롭 정보와 함께 상품 표시 정보까지 담는다.
     *
     * 드롭당 상품 조회가 한 번 더 붙지만, 하루 드롭은 많아야 TimeSlot 수(5개)이고
     * 이 메서드는 등록·수정·삭제·자정·기동 때만 호출된다. 반면 입장 확정은 요청마다 일어나므로,
     * 여기서 한 번 읽어두면 핫패스의 상품 조회(상품·재고·픽업일 3개 쿼리)가 통째로 사라진다.
     */
    public void refresh() {
        LocalDate today = LocalDate.now();
        List<CachedDrop> dropList = dropRepository.findListByDropDate(today).stream()
                .map(drop -> CachedDrop.of(drop, productPort.getProductInfo(drop.getProductId())))
                .toList();

        cachedDrop.set(dropList);
    }

    public List<CachedDrop> get() {
        return cachedDrop.get();
    }

    // 하루에 진행되는 드롭은 많아야 TimeSlot 수(5개)라 순회로 충분하다.
    // 비어 있으면 "오늘 진행되는 드롭이 아님"을 뜻한다(어제 드롭이거나 아직 등록 전).
    public Optional<CachedDrop> find(Long dropId) {
        return cachedDrop.get().stream()
                .filter(drop -> drop.dropId().equals(dropId))
                .findFirst();
    }

}