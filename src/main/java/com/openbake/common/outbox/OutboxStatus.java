package com.openbake.common.outbox;

/** v1은 PENDING/PUBLISHED만 쓴다. 영구 실패를 별도 상태로 확정하지 않고, 재시도로 계속 회복을 시도한다. */
public enum OutboxStatus {
    PENDING,
    PUBLISHED
}
