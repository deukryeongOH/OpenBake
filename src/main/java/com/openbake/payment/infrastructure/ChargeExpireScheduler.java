package com.openbake.payment.infrastructure;

import com.openbake.payment.application.ChargeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 충전 요청 만료 배치.
 * 30분 동안 READY 상태로 남아있는 충전 요청을 EXPIRED로 바꾼다.
 *
 * 왜 필요한가?
 * 사용자가 충전 요청만 하고 PG 결제창을 완료하지 않으면 READY 상태로 남는다.
 * 이걸 정리하지 않으면 "이미 진행 중인 충전 요청이 있습니다" 에러가 계속 뜸.
 *
 * 5분마다 실행. @EnableScheduling이 메인 애플리케이션에 있어야 동작한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChargeExpireScheduler {

    private final ChargeService chargeService;

    @Scheduled(fixedRate = 5 * 60 * 1000)  // 5분마다
    public void expireStaleChargeRequests() {
        log.info("[배치] 만료 충전 요청 정리 시작");
        chargeService.expireStaleRequests();
        log.info("[배치] 만료 충전 요청 정리 완료");
    }
}
