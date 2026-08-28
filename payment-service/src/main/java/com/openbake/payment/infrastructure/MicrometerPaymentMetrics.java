package com.openbake.payment.infrastructure;

import com.openbake.payment.application.port.PaymentMetricsPort;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/**
 * {@link PaymentMetricsPort}의 Micrometer 구현.
 *
 * <p><b>카운터를 생성자에서 미리 등록한다.</b> {@code registry.counter(...)}를 호출
 * 시점에 만들면 한 번도 발생하지 않은 사건의 지표는 아예 노출되지 않는다. 그러면
 * 대시보드에서 "0건이라 안 보이는 것"과 "계측이 빠져서 안 보이는 것"을 구별할 수
 * 없다. 미리 등록하면 평소 0으로 보이고, 0에서 움직이는 순간이 곧 신호가 된다.
 *
 * <p>이 구분이 중요한 이유는 2026-08-27에 겪었다. {@code ElasticsearchDown} 경보가
 * 존재하지 않는 지표 이름을 참조해 <b>영원히 발화하지 않는 상태</b>였는데, 오류가
 * 나지 않아 "정상이라 안 울리는 것"과 구별되지 않았다. 지표도 같은 함정이 있다.
 *
 * <p>지표 이름을 이 클래스 안에만 두는 것도 같은 맥락이다. 여러 곳에서 문자열로
 * 복제하면 하나만 어긋나도 조용히 빈다.
 */
@Component
public class MicrometerPaymentMetrics implements PaymentMetricsPort {

    private final Counter refundFailed;
    private final Counter payFailed;
    private final Counter idempotencyConflictPay;
    private final Counter idempotencyConflictRefund;
    private final Counter executionFailed;

    public MicrometerPaymentMetrics(MeterRegistry registry) {
        this.refundFailed = Counter.builder("openbake.payment.refund.failed")
                .description("환불이 실패로 기록된 횟수")
                .register(registry);
        this.payFailed = Counter.builder("openbake.payment.pay.failed")
                .description("결제가 실패로 기록된 횟수")
                .register(registry);
        // 같은 이름에 operation 라벨만 다르게 둔다. 대시보드에서 합계와 분해를
        // 모두 볼 수 있어야 "환불만 실패가 몰린다" 같은 판단이 가능하다.
        this.idempotencyConflictPay = Counter.builder("openbake.payment.idempotency.conflict")
                .description("멱등 레코드 생성 경합으로 재조회한 횟수")
                .tag("operation", "pay")
                .register(registry);
        this.idempotencyConflictRefund = Counter.builder("openbake.payment.idempotency.conflict")
                .description("멱등 레코드 생성 경합으로 재조회한 횟수")
                .tag("operation", "refund")
                .register(registry);
        this.executionFailed = Counter.builder("openbake.payment.execution.failed")
                .description("재조회까지 실패해 결과가 불확실하게 끝난 횟수")
                .register(registry);
    }

    @Override
    public void refundFailed() {
        refundFailed.increment();
    }

    @Override
    public void payFailed() {
        payFailed.increment();
    }

    @Override
    public void idempotencyConflict(String operation) {
        if ("refund".equals(operation)) {
            idempotencyConflictRefund.increment();
        } else {
            idempotencyConflictPay.increment();
        }
    }

    @Override
    public void executionFailed() {
        executionFailed.increment();
    }
}
