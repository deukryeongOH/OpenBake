package com.openbake.order.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

/**
 * 주문 발신함(Outbox). 구매확정 이벤트를 정산 도메인으로 전달하기 위한 발신 레코드다.
 * 주문 상태 변경과 같은 트랜잭션에서 저장되고(PENDING), 릴레이 배치가 읽어 전송한다.
 * 릴레이가 WHERE status='PENDING' ORDER BY created_at 으로 폴링하므로
 * (status, created_at) 복합 인덱스를 둔다.
 */
@Entity
@Table(
        name = "order_outbox_events",
        indexes = @Index(name = "idx_outbox_status_created", columnList = "status, created_at")
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderOutboxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    //이벤트 고유 ID(UUID). 정산 source_event_id 와 대조되는 멱등 키.
    @Column(nullable = false, unique = true)
    private String eventId;

    @Column(nullable = false)
    private String eventType;

    @Column(nullable = false)
    private int eventVersion;

    //다형성 참조(실FK 아님). 어떤 애그리거트의 이벤트인지.
    @Column(nullable = false)
    private String aggregateType;

    @Column(nullable = false)
    private Long aggregateId;

    //전송할 이벤트 본문(JSON 문자열). 발행 시점에 직렬화해 담는다.
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OutboxStatus status;

    @Column(nullable = false)
    private int retryCount;

    //전송 실패 사유(FAILED 이거나 재시도 중일 때만).
    private String failureReason;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    //전송 완료 시각(SENT 일 때만).
    private LocalDateTime sentAt;

    //구매확정 이벤트 발신 레코드 생성. 생성 시 PENDING.
    public static OrderOutboxEvent createPurchaseConfirmed(String eventId, Long orderId, String payload) {
        OrderOutboxEvent event = new OrderOutboxEvent();
        event.eventId = eventId;
        event.eventType = "PurchaseConfirmed";
        event.eventVersion = 1;
        event.aggregateType = "ORDER";
        event.aggregateId = orderId;
        event.payload = payload;
        event.status = OutboxStatus.PENDING;
        event.retryCount = 0;
        event.createdAt = LocalDateTime.now();
        return event;
    }

    //전송 성공.
    public void markSent() {
        this.status = OutboxStatus.SENT;
        this.sentAt = LocalDateTime.now();
    }

    //전송 실패 1회 기록. status 는 PENDING 을 유지해 다음 주기에 재시도된다.
    public void recordFailure(String reason) {
        this.retryCount++;
        this.failureReason = reason;
    }

    //재시도 상한 초과 시 영구 실패 처리.
    public void markFailed() {
        this.status = OutboxStatus.FAILED;
    }
}
