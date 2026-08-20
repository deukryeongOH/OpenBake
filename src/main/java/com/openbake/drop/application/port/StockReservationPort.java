package com.openbake.drop.application.port;

import java.time.Duration;

/**
 * 드롭 진행 중 재고의 소유권을 갖는 원자적 카운터.
 *
 * 드롭이 진행되는 동안 실시간 잔여 재고는 이 카운터가 정본이고,
 * product_inventory 는 주기적으로 동기화되는 파생값이다.
 * 복구 원본은 drop_entry 이며, 카운터가 유실되면 그 합계로 재계산한다.
 */
public interface StockReservationPort {

    int NOT_INITIALIZED = -1;
    int OUT_OF_STOCK = -2;

    /**
     * 재고를 원자적으로 선점한다.
     *
     * @return 차감 후 잔여 수량. 0 이면 마지막 재고를 가져간 것이므로 호출자가 품절 처리를 해야 한다.
     *         {@link #NOT_INITIALIZED} 키 없음(미초기화), {@link #OUT_OF_STOCK} 재고 부족.
     */
    long reserve(Long dropId, int quantity);

    /**
     * 선점을 되돌린다. 총 수량을 넘어서는 비정상 롤백은 거부한다.
     *
     * @return 복구 후 잔여 수량. {@link #NOT_INITIALIZED} 키 없음, {@link #OUT_OF_STOCK} 총 수량 초과.
     */
    long rollback(Long dropId, int quantity, int totalQuantity);

    /**
     * 키가 없을 때만 초기화한다.
     *
     * 무조건 SET 하면 TodayDropCache.refresh() 로 마킹이 초기화된 뒤 재워밍업이 일어나면서
     * 진행 중인 드롭의 잔여 수량을 되살려 초과 판매로 이어진다. 반드시 조건부여야 한다.
     *
     * @return 실제로 초기화했으면 true, 이미 있어서 건너뛰었으면 false
     */
    boolean initIfAbsent(Long dropId, int remainQuantity, Duration ttl);

    /** 동기화·조회용 현재값. 키가 없으면 null. */
    Long peek(Long dropId);

    void clear(Long dropId);
}