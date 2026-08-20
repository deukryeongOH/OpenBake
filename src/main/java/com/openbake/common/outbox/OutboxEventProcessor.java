package com.openbake.common.outbox;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Outbox 한 건을 claim → 발행 → 상태 갱신까지 하나의 트랜잭션으로 처리한다.
 * OutboxPublisher(스케줄러)가 이 메서드를 반복 호출하는 구조로 분리한 이유는,
 * 같은 클래스 안에서 @Scheduled 메서드가 @Transactional 메서드를 직접 호출하면
 * 셀프 인보케이션 때문에 트랜잭션이 걸리지 않기 때문이다 (AutoConfirmScheduler → OrderService 와 동일 패턴).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OutboxEventProcessor {

    private static final Duration RETRY_DELAY = Duration.ofSeconds(10);

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaOutboxSender kafkaOutboxSender;

    /** claim할 PENDING 행이 없으면 false. 발행 성공/실패와 무관하게 뭔가 처리했으면 true. */
    @Transactional
    public boolean processNext() {
        Optional<OutboxEvent> claimed = outboxEventRepository.claimNext();
        if (claimed.isEmpty()) {
            return false;
        }

        OutboxEvent event = claimed.get();
        try {
            kafkaOutboxSender.send(event);
            event.markPublished();
        } catch (Exception e) {
            event.scheduleRetry(Instant.now().plus(RETRY_DELAY));
            log.error(
                    "[Outbox] 발행 실패, {}초 후 재시도 eventId={}, topic={}, attemptCount={}, reason={}",
                    RETRY_DELAY.getSeconds(),
                    event.getEventId(),
                    event.getTopic(),
                    event.getAttemptCount(),
                    e.getMessage());
        }
        return true;
    }
}
