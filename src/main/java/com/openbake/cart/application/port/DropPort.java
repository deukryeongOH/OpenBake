package com.openbake.cart.application.port;

import com.openbake.cart.application.port.dto.DropInfo;

/**
 * 드롭 조회 포트.
 *
 * cart 는 drop 의 저장소/엔티티를 직접 보지 않고 이 인터페이스만 안다.
 * 지금 구현체는 같은 코어 안의 저장소를 호출하지만, drop 이 분리되면
 * infrastructure 의 구현체만 FeignClient 로 갈아끼우면 된다.
 */
public interface DropPort {

    //드롭이 없으면 DROP_NOT_FOUND.
    DropInfo getDrop(Long dropId);
}
