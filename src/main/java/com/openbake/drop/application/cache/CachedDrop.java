package com.openbake.drop.application.cache;

import com.openbake.drop.application.dto.DropProductInfoResult;
import com.openbake.drop.domain.entity.Drop;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 여기 담는 값은 "드롭이 시작된 뒤 바뀌지 않는 것"만 넣는다.
 * Drop.isEditable()이 UPCOMING일 때만 수정을 허용하고, 드롭 상품은 일반 상품 수정 경로에서도 막히므로
 * productId/limitQuantity/시작·종료 시각과 상품 표시 정보는 시작 후 불변이다.
 * 캐시 갱신(등록/수정/삭제/자정/기동)만으로 최신성이 보장된다.
 *
 * 넣지 않는 것이 둘 있다.
 * - dropStatus: 진행 중에도 UPCOMING -> ACTIVE -> (품절)COMPLETED -> (롤백)ACTIVE 로 계속 바뀌는데
 *   이 전환들은 refresh()를 호출하지 않으므로 캐시가 곧바로 낡은 값이 된다.
 * - remainQuantity: 요청마다 바뀐다. 드롭 중 정본은 Redis 카운터다.
 */
public record CachedDrop(LocalDate cachedDate, Long dropId, Long productId, LocalDateTime dropStart, LocalDateTime dropEnd, AtomicBoolean checkStarted, AtomicBoolean checkEnded, int limitQuantity,
                         String name, String description, String imageUrl, int price, Set<LocalDate> pickupDates) {

    static CachedDrop of(Drop drop, DropProductInfoResult product) {
        return new CachedDrop(LocalDate.now(), drop.getId(), drop.getProductId(), drop.getDropStart(), drop.getDropEnd(),
                new AtomicBoolean(false), new AtomicBoolean(false), drop.getLimitQuantity(),
                product.name(), product.description(), product.imageUrl(), product.price(),
                // 지연 컬렉션을 복사해 담는다. 캐시는 세션 밖에서 읽히므로 참조만 들고 있으면
                // 나중에 초기화를 시도하다 LazyInitializationException 이 난다.
                Set.copyOf(product.pickUpAvailableDates()));
    }

    // true를 반환하는 최초 1회에만 Active 전환을 수행
    public boolean tryMarkStarted() {
        return checkStarted.compareAndSet(false, true);
    }

    // true를 반환하는 최초 1회에만 Completed 전환을 수행
    public boolean tryMarkEnded() {
        return checkEnded.compareAndSet(false, true);
    }
}