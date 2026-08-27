package com.openbake.order.application;

import com.openbake.order.domain.OrderRepository;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Saga가 불확실한 결제를 PENDING으로 보존하는 전략은, 그 PENDING이 실제로 줄어드는지
 * 볼 수 있을 때만 성립한다. 만료 배치가 멈추거나 안전 환불이 계속 실패하면 여기 쌓인다.
 */
@Component
public class OrderMetricsRefresher {

    private final OrderRepository orderRepository;
    private final AtomicLong expiredPending = new AtomicLong();
    private final AtomicLong expiredPendingOldestAgeSeconds = new AtomicLong();
    private final AtomicLong leakedActiveSlots = new AtomicLong();

    public OrderMetricsRefresher(OrderRepository orderRepository, MeterRegistry registry) {
        this.orderRepository = orderRepository;
        registry.gauge("openbake.order.pending.expired", expiredPending);
        registry.gauge("openbake.order.pending.oldest_age_seconds", expiredPendingOldestAgeSeconds);
        // 종료 상태인데 슬롯이 남은 주문. 0이 아니면 상태 전이 경로에 구멍이 있다는 뜻이라
        // 임계값을 정할 필요 없이 0 초과 자체가 조사 대상이다.
        registry.gauge("openbake.order.active_slot.leaked", leakedActiveSlots);
    }

    @Scheduled(
            fixedDelayString = "${openbake.order.metrics.refresh-interval:PT30S}",
            initialDelayString = "${openbake.order.metrics.refresh-interval:PT30S}")
    public void refresh() {
        LocalDateTime now = LocalDateTime.now();
        expiredPending.set(orderRepository.countExpiredPending(now));
        long age = orderRepository.findOldestExpiredPendingAt(now)
                .map(expiresAt -> Math.max(0, Duration.between(expiresAt, now).toSeconds()))
                .orElse(0L);
        expiredPendingOldestAgeSeconds.set(age);
        leakedActiveSlots.set(orderRepository.countLeakedActiveSlots());
    }
}
