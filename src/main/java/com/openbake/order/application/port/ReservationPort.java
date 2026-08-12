package com.openbake.order.application.port;

public interface ReservationPort {
    void rollbackStock(Long dropId, Long memberId);
}