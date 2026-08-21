package com.openbake.member.outbox;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Transactional Outbox 레코드. 도메인 상태 변경과 같은 트랜잭션에서 PENDING으로 저장되고,
 * OutboxPublisher가 커밋 이후 별도로 Kafka에 발행한다.
 * eventId는 여기서 한 번만 생성되고, 발행 재시도 시에도 그대로 유지된다.
 */
@Entity
@Table(name = "outbox_events")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OutboxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "event_id", nullable = false, unique = true, length = 100)
    private String eventId;

    @Column(nullable = false, length = 100)
    private String topic;

    @Column(name = "event_key", nullable = false, length = 100)
    private String eventKey;

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    @Column(name = "event_version", nullable = false)
    private Integer eventVersion;

    @Column(nullable = false, columnDefinition = "text")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OutboxStatus status;

    @Column(name = "attempt_count", nullable = false)
    private Integer attemptCount;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "next_attempt_at")
    private Instant nextAttemptAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    public static OutboxEvent create(
            String topic, String eventKey, String eventType, int eventVersion, String payload, Instant occurredAt) {
        return create(UUID.randomUUID().toString(), topic, eventKey, eventType, eventVersion, payload, occurredAt);
    }

    public static OutboxEvent create(
            String eventId, String topic, String eventKey, String eventType,
            int eventVersion, String payload, Instant occurredAt) {
        OutboxEvent event = new OutboxEvent();
        event.eventId = eventId;
        event.topic = topic;
        event.eventKey = eventKey;
        event.eventType = eventType;
        event.eventVersion = eventVersion;
        event.payload = payload;
        event.occurredAt = occurredAt;
        event.status = OutboxStatus.PENDING;
        event.attemptCount = 0;
        return event;
    }

    public void markPublished() {
        this.status = OutboxStatus.PUBLISHED;
        this.publishedAt = Instant.now();
    }

    //일시적 발행 실패. 상태는 PENDING을 유지하고 다음 시도 시각만 미룬다 — 영구 실패 상태는 두지 않는다.
    public void scheduleRetry(Instant nextAttemptAt) {
        this.attemptCount++;
        this.nextAttemptAt = nextAttemptAt;
    }
}
