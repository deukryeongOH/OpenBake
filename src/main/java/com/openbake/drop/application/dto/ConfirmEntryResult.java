package com.openbake.drop.application.dto;

import com.openbake.drop.application.cache.CachedDrop;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

public record ConfirmEntryResult(
    String name, String description, String imageUrl, int price, int limitQuantity, int remainQuantity, Set<LocalDate> pickupDates
) {

    // pickupDates가 LAZY 컬렉션이라, 호출부의 트랜잭션(세션)이 열려 있는 동안
    // 새 HashSet으로 복사해둬야 한다. 참조만 넘기면 세션이 끝난 뒤(presentation에서 직렬화할 때)
    // 초기화를 시도하다 LazyInitializationException이 난다.
    public static ConfirmEntryResult of(DropProductInfoResult result, int limitQuantity){
        return new ConfirmEntryResult(
                result.name(), result.description(),
                result.imageUrl(), result.price(), limitQuantity, result.remainQuantity(), new HashSet<>(result.pickUpAvailableDates())
        );
    }

    /**
     * 캐시된 상품 스냅샷 + 실시간 잔여 수량으로 조립한다.
     *
     * 표시 정보는 드롭 중 불변이라 캐시가 정확하고, 잔여 수량만 드롭 중 정본인 Redis 카운터에서 받는다.
     * pickupDates는 캐시에 이미 복사본으로 들어 있어 여기서 다시 복사하지 않는다.
     */
    public static ConfirmEntryResult of(CachedDrop drop, int remainQuantity){
        return new ConfirmEntryResult(
                drop.name(), drop.description(), drop.imageUrl(), drop.price(),
                drop.limitQuantity(), remainQuantity, drop.pickupDates()
        );
    }
}