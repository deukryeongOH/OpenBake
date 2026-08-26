package com.openbake.payment.infrastructure;

import com.openbake.payment.domain.ChargeRequest;
import com.openbake.payment.domain.ChargeRequestRepository;
import com.openbake.payment.domain.ChargeStatus;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 결제 미결 상태를 계측한다.
 *
 * <p>충전은 PG 결과가 불확실하면 {@code IN_PROGRESS}로 보존하고 5분 주기 배치가
 * 다시 조회해 수렴시킨다. 이 전략은 미결 건이 실제로 줄어드는지 볼 수 있을 때만
 * 성립한다. 배치가 멈추거나 PG 조회가 계속 실패하면 여기에 쌓인다.
 *
 * <p>2026-08-26까지 payment-service에는 커스텀 지표가 하나도 없었다.
 * {@code docs/deep-dives/payment-saga-orchestration.md} 10장이 요구하는 항목 중
 * 미결 건수와 나이를 먼저 채운다.
 */
@Component
public class PaymentMetricsRefresher {

    private final ChargeRequestRepository chargeRequestRepository;
    private final AtomicLong inProgress = new AtomicLong();
    private final AtomicLong oldestInProgressAgeSeconds = new AtomicLong();

    public PaymentMetricsRefresher(
            ChargeRequestRepository chargeRequestRepository, MeterRegistry registry) {
        this.chargeRequestRepository = chargeRequestRepository;
        // 결과가 불확실해 보존 중인 충전. 사용자 돈이 묶여 있다는 뜻이다.
        registry.gauge("openbake.payment.charge.in_progress", inProgress);
        // 건수가 적어도 한 건이 오래 남아 있으면 그 사용자에겐 심각하다.
        registry.gauge(
                "openbake.payment.charge.oldest_in_progress_age_seconds", oldestInProgressAgeSeconds);
    }

    @Scheduled(
            fixedDelayString = "${openbake.payment.metrics.refresh-interval:PT30S}",
            initialDelayString = "${openbake.payment.metrics.refresh-interval:PT30S}")
    public void refresh() {
        List<ChargeRequest> pending = chargeRequestRepository.findByStatus(ChargeStatus.IN_PROGRESS);
        inProgress.set(pending.size());

        LocalDateTime now = LocalDateTime.now();
        long oldest = pending.stream()
                .map(ChargeRequest::getRequestedAt)
                .filter(java.util.Objects::nonNull)
                .mapToLong(requestedAt -> Math.max(0, Duration.between(requestedAt, now).toSeconds()))
                .max()
                .orElse(0L);
        oldestInProgressAgeSeconds.set(oldest);
    }
}
