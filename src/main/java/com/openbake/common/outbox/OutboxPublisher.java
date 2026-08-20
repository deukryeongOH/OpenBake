package com.openbake.common.outbox;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Outbox 발행 배치. 한 번 깨어날 때마다 최대 BATCH_SIZE건까지 claim해서 순서대로 발행한다.
 * 이전 실행이 끝난 뒤부터 세는 fixedDelay라 실행이 겹치지 않는다 (AutoConfirmScheduler와 동일 정책).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxPublisher {

    private static final int BATCH_SIZE = 20;

    private final OutboxEventProcessor outboxEventProcessor;

    @Scheduled(fixedDelayString = "${openbake.outbox.publish-interval:PT2S}")
    public void run() {
        int processed = 0;
        while (processed < BATCH_SIZE && outboxEventProcessor.processNext()) {
            processed++;
        }
        if (processed > 0) {
            log.info("[Outbox] 이번 배치 처리 {}건", processed);
        }
    }
}
