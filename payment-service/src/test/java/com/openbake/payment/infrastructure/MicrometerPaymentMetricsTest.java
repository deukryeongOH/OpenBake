package com.openbake.payment.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 결제 지표가 <b>발생 전에도 노출되는지</b>를 검증한다.
 *
 * <p>이 테스트가 지키는 것은 값의 정확성이 아니라 <b>지표의 존재</b>다.
 * {@code registry.counter(...)}를 사건 발생 시점에 호출하면 한 번도 일어나지 않은
 * 사건의 지표는 아예 노출되지 않는다. 그러면 대시보드에서 "0건이라 안 보이는 것"과
 * "계측이 빠져서 안 보이는 것"을 구별할 수 없다.
 *
 * <p>2026-08-27에 {@code ElasticsearchDown} 경보가 존재하지 않는 지표 이름을 참조해
 * 영원히 발화하지 않는 상태였는데, 오류가 나지 않아 "정상이라 안 울리는 것"과
 * 구별되지 않았다. 지표에도 같은 함정이 있고, 이 테스트가 그 재발을 막는다.
 */
class MicrometerPaymentMetricsTest {

    private static final List<String> EXPECTED_NAMES = List.of(
            "openbake.payment.refund.failed",
            "openbake.payment.pay.failed",
            "openbake.payment.idempotency.conflict",
            "openbake.payment.execution.failed");

    @Test
    @DisplayName("아무 사건도 없어도 모든 카운터가 0으로 노출된다")
    void exposesAllCountersBeforeAnyEvent() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();

        new MicrometerPaymentMetrics(registry);

        for (String name : EXPECTED_NAMES) {
            List<Counter> counters = registry.find(name).counters().stream().toList();
            assertThat(counters)
                    .as("%s 가 미리 등록되어 있어야 한다", name)
                    .isNotEmpty();
            assertThat(counters).allSatisfy(counter ->
                    assertThat(counter.count()).isZero());
        }
    }

    @Test
    @DisplayName("멱등 경합은 operation 라벨로 결제와 환불이 분리된다")
    void separatesIdempotencyConflictByOperation() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        MicrometerPaymentMetrics metrics = new MicrometerPaymentMetrics(registry);

        metrics.idempotencyConflict("pay");
        metrics.idempotencyConflict("refund");
        metrics.idempotencyConflict("refund");

        assertThat(counter(registry, "pay")).isEqualTo(1.0);
        assertThat(counter(registry, "refund")).isEqualTo(2.0);
    }

    @Test
    @DisplayName("알 수 없는 operation은 pay로 센다 — 값을 잃지 않는다")
    void unknownOperationFallsBackToPay() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        MicrometerPaymentMetrics metrics = new MicrometerPaymentMetrics(registry);

        metrics.idempotencyConflict("something-else");

        // 새 라벨을 만들어 카디널리티를 늘리는 것보다, 알려진 값으로 접어 넣는 편이
        // 낫다. 사건이 조용히 사라지지 않는 것이 더 중요하다.
        assertThat(counter(registry, "pay")).isEqualTo(1.0);
    }

    @Test
    @DisplayName("각 기록 메서드가 해당 카운터만 올린다")
    void eachMethodIncrementsItsOwnCounter() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        MicrometerPaymentMetrics metrics = new MicrometerPaymentMetrics(registry);

        metrics.refundFailed();
        metrics.executionFailed();

        assertThat(registry.get("openbake.payment.refund.failed").counter().count()).isEqualTo(1.0);
        assertThat(registry.get("openbake.payment.execution.failed").counter().count()).isEqualTo(1.0);
        assertThat(registry.get("openbake.payment.pay.failed").counter().count()).isZero();
    }

    private static double counter(SimpleMeterRegistry registry, String operation) {
        return registry.get("openbake.payment.idempotency.conflict")
                .tag("operation", operation)
                .counter()
                .count();
    }
}
