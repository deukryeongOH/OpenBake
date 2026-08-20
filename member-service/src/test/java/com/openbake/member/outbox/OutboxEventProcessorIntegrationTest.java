package com.openbake.member.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Outbox의 at-least-once 발행과 중복 claim 방지를 실제 DB(H2, 트랜잭션/락 포함)로 검증한다.
 * Kafka 자체는 목으로 대체해 DB claim 로직만 격리해서 본다 — 실제 Kafka 전달은 별도 관심사다.
 * 동시성 테스트가 있어 클래스 레벨 @Transactional을 쓰지 않는다(각 스레드가 독립된 트랜잭션/커넥션을 열어야
 * SKIP LOCKED가 의미가 있다). 대신 매 테스트 후 데이터를 직접 정리한다.
 */
@SpringBootTest
@ActiveProfiles("test")
class OutboxEventProcessorIntegrationTest {

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private OutboxEventProcessor outboxEventProcessor;

    @MockitoBean
    private KafkaOutboxSender kafkaOutboxSender;

    @AfterEach
    void cleanUp() {
        outboxEventRepository.deleteAll();
    }

    @Test
    @DisplayName("PENDING 행을 발행하면 PUBLISHED로 바뀌고 eventId는 그대로 유지된다")
    void publishSuccess_marksAsPublishedAndKeepsSameEventId() {
        OutboxEvent saved = outboxEventRepository.saveAndFlush(
                OutboxEvent.create("member.withdrawn.v1", "1", "WITHDRAWN", 1, "{}", Instant.now()));
        String originalEventId = saved.getEventId();

        boolean processed = outboxEventProcessor.processNext();

        assertThat(processed).isTrue();
        OutboxEvent reloaded = outboxEventRepository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(OutboxStatus.PUBLISHED);
        assertThat(reloaded.getEventId()).isEqualTo(originalEventId);
        assertThat(reloaded.getPublishedAt()).isNotNull();
        verify(kafkaOutboxSender, times(1)).send(any());
    }

    @Test
    @DisplayName("이미 PUBLISHED인 행은 다시 claim되지 않는다 — 중복 발행 방지")
    void publishedEvent_isNotReclaimed() {
        outboxEventRepository.saveAndFlush(
                OutboxEvent.create("member.withdrawn.v1", "1", "WITHDRAWN", 1, "{}", Instant.now()));
        outboxEventProcessor.processNext();

        boolean reclaimed = outboxEventProcessor.processNext();

        assertThat(reclaimed).isFalse();
        verify(kafkaOutboxSender, times(1)).send(any());
    }

    @Test
    @DisplayName("발행 실패 시 PENDING을 유지하고 attemptCount를 늘리며 즉시 재시도 대상이 되지 않는다")
    void publishFailure_staysPendingAndBacksOff() {
        doThrow(new IllegalStateException("kafka down")).when(kafkaOutboxSender).send(any());
        OutboxEvent saved = outboxEventRepository.saveAndFlush(
                OutboxEvent.create("member.withdrawn.v1", "1", "WITHDRAWN", 1, "{}", Instant.now()));

        boolean processed = outboxEventProcessor.processNext();
        assertThat(processed).isTrue();

        OutboxEvent reloaded = outboxEventRepository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(reloaded.getAttemptCount()).isEqualTo(1);
        assertThat(reloaded.getNextAttemptAt()).isAfter(Instant.now());

        //next_attempt_at이 미래라 이번엔 claim 대상에서 제외된다.
        boolean reclaimedImmediately = outboxEventProcessor.processNext();
        assertThat(reclaimedImmediately).isFalse();
    }

    @Test
    @DisplayName("여러 스레드가 동시에 claim해도 같은 행을 중복 발행하지 않는다 (SKIP LOCKED)")
    void concurrentClaims_doNotDoubleProcessSameRow() throws InterruptedException {
        int rowCount = 10;
        for (int i = 0; i < rowCount; i++) {
            outboxEventRepository.saveAndFlush(
                    OutboxEvent.create("member.withdrawn.v1", String.valueOf(i), "WITHDRAWN", 1, "{}", Instant.now()));
        }

        int threadCount = 5;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger totalProcessed = new AtomicInteger();

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    while (outboxEventProcessor.processNext()) {
                        totalProcessed.incrementAndGet();
                    }
                } finally {
                    latch.countDown();
                }
            });
        }
        boolean finished = latch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(finished).isTrue();
        assertThat(totalProcessed.get()).isEqualTo(rowCount);
        //행마다 정확히 한 번씩만 send가 불렸다 — 두 스레드가 같은 행을 동시에 집어가지 않았다는 뜻.
        verify(kafkaOutboxSender, times(rowCount)).send(any());
        long publishedCount = outboxEventRepository.findAll().stream()
                .filter(event -> event.getStatus() == OutboxStatus.PUBLISHED)
                .count();
        assertThat(publishedCount).isEqualTo(rowCount);
    }
}
