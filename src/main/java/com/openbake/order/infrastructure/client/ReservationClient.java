package com.openbake.order.infrastructure.client;

import com.openbake.drop.application.service.DropLockService;
import com.openbake.order.application.port.ReservationPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * ReservationPort 구현체. drop 이 아직 같은 코어 안에 있어 서비스를 직접 호출한다.
 * drop 의 타입은 이 파일 밖으로 나가지 않는다.
 */
//cart 에도 같은 이름의 어댑터가 있어 빈 이름을 명시한다(클래스 단순명이 겹치면 기동 실패).
@Component("orderReservationClient")
@RequiredArgsConstructor
public class ReservationClient implements ReservationPort {

    private final DropLockService dropLockService;

    /**
     * 복구 수량은 넘기지 않는다. drop 이 DropEntry.selectQuantity 에 저장해 둔 값을 읽어 되돌린다.
     *
     * 응모 이력이 없으면 NEVER_ENTERED 가 올라온다. 여기서 삼키면 재고 복구가 실패했는데도
     * 취소가 성공한 것처럼 보이므로 그대로 흘려보낸다.
     */
    @Override
    public void rollbackStock(Long dropId, Long memberId) {
        dropLockService.rollbackStock(dropId, memberId);
    }
}
