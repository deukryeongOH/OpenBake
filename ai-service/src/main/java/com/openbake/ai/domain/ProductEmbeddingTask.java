package com.openbake.ai.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "product_embedding_tasks")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductEmbeddingTask {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_id", nullable = false, unique = true)
    private Long productId;

    @Column(name = "latest_event_id", nullable = false, unique = true)
    private UUID latestEventId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 20)
    private ProductChangeType eventType;

    @Column(length = 255)
    private String name;

    @Column(columnDefinition = "text")
    private String description;

    @Column(length = 100)
    private String category;

    @Column(name = "product_type", length = 20)
    private String productType;

    @Column(name = "source_hash", length = 100)
    private String sourceHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EmbeddingTaskStatus status;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    @Column(name = "next_attempt_at")
    private Instant nextAttemptAt;

    @Column(name = "lease_expires_at")
    private Instant leaseExpiresAt;

    @Column(name = "last_error_code", length = 100)
    private String lastErrorCode;

    @Column(name = "source_occurred_at", nullable = false)
    private Instant sourceOccurredAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static ProductEmbeddingTask from(ProductChangedEvent event, Instant now) {
        ProductEmbeddingTask task = new ProductEmbeddingTask();
        task.productId = event.productId();
        task.createdAt = now;
        task.apply(event, now);
        return task;
    }

    public void apply(ProductChangedEvent event, Instant now) {
        latestEventId = event.eventId();
        eventType = event.eventType();
        name = event.name();
        description = event.description();
        category = event.category();
        productType = event.type();
        sourceHash = null;
        status = EmbeddingTaskStatus.PENDING;
        retryCount = 0;
        nextAttemptAt = null;
        leaseExpiresAt = null;
        lastErrorCode = null;
        sourceOccurredAt = event.occurredAt();
        updatedAt = now;
    }

    public void claim(Instant now, Duration leaseDuration) {
        status = EmbeddingTaskStatus.PROCESSING;
        leaseExpiresAt = now.plus(leaseDuration);
        nextAttemptAt = null;
        updatedAt = now;
    }

    public void recordSourceHash(String sourceHash, Instant now) {
        this.sourceHash = sourceHash;
        updatedAt = now;
    }

    public void complete(Instant now) {
        status = EmbeddingTaskStatus.COMPLETED;
        nextAttemptAt = null;
        leaseExpiresAt = null;
        lastErrorCode = null;
        updatedAt = now;
    }

    public void scheduleRetry(String errorCode, Instant nextAttemptAt, Instant now) {
        retryCount++;
        status = EmbeddingTaskStatus.PENDING;
        this.nextAttemptAt = nextAttemptAt;
        leaseExpiresAt = null;
        lastErrorCode = errorCode;
        updatedAt = now;
    }

    public void fail(String errorCode, Instant now) {
        status = EmbeddingTaskStatus.FAILED;
        nextAttemptAt = null;
        leaseExpiresAt = null;
        lastErrorCode = errorCode;
        updatedAt = now;
    }

    /** 실시간 이벤트 순서를 보존하면서 core의 현재 원문으로 복구 작업을 재예약한다. */
    public void rescheduleFromRecovery(
            String name, String description, String category, String productType, Instant now) {
        // latestEventId/sourceOccurredAt은 건드리지 않아 이후 실시간 이벤트의 순서가 우선한다.
        eventType = ProductChangeType.UPDATED;
        this.name = name;
        this.description = description;
        this.category = category;
        this.productType = productType;
        sourceHash = null;
        resetPending(now);
    }

    public boolean retryIfRecoverable(Instant now) {
        boolean recoverable = status == EmbeddingTaskStatus.FAILED
                || (status == EmbeddingTaskStatus.PROCESSING
                    && leaseExpiresAt != null && leaseExpiresAt.isBefore(now));
        if (!recoverable) {
            return false;
        }
        retryCount = 0;
        resetPending(now);
        return true;
    }

    private void resetPending(Instant now) {
        status = EmbeddingTaskStatus.PENDING;
        retryCount = 0;
        nextAttemptAt = null;
        leaseExpiresAt = null;
        lastErrorCode = null;
        updatedAt = now;
    }
}
