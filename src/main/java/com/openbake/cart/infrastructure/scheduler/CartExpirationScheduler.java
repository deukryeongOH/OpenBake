package com.openbake.cart.infrastructure.scheduler;

import com.openbake.cart.application.CartService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 만료된 장바구니를 주기적으로 정리한다.
 *
 * 사용자가 DELETE 를 호출하지 않고 이탈하는 경우(브라우저 종료 등)가 대부분이므로,
 * 이 배치가 선점 재고를 회수하는 실질적인 수단이다.
 *
 * 월 정산과 달리 실행 이력·재시작이 필요 없는 단순 삭제라 Spring Batch 를 쓰지 않고
 * 스케줄러(@Scheduled)로 처리한다.
 *
 * 주기는 openbake.cart.expiration-delay 로 조정한다.
 * 짧을수록 재고 회수가 빠르지만 조회 부하가 늘어난다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CartExpirationScheduler {

    private final CartService cartService;

    //이전 실행이 끝난 뒤부터 delay 를 세므로 실행이 겹치지 않는다.
    @Scheduled(fixedDelayString = "${openbake.cart.expiration-delay}")
    public void expireCarts() {
        int expiredCount = cartService.expireCarts(LocalDateTime.now());

        //정리할 게 없을 때가 대부분이라 그때는 로그를 남기지 않는다.
        if (expiredCount > 0) {
            log.info("만료된 장바구니를 정리했습니다. count={}", expiredCount);
        }
    }
}
