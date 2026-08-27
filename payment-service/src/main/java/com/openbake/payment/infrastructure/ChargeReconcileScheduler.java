package com.openbake.payment.infrastructure;

import com.openbake.payment.application.ChargeReconcileService;
import com.openbake.payment.domain.ChargeRequest;
import com.openbake.payment.domain.ChargeRequestRepository;
import com.openbake.payment.domain.ChargeStatus;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 미결 충전 확인 배치.
 * IN_PROGRESS 상태로 남아있는 충전 요청을 토스페이먼츠에 직접 조회해서 해소한다.
 *
 * 왜 필요한가?
 * 서버가 토스 승인 API 응답을 못 받으면(타임아웃, 서버 다운 등) IN_PROGRESS로 남는다.
 * 이때 돈은 이미 나갔을 수 있다. 배치가 토스에 직접 물어봐서 결과를 반영해야 한다.
 * 웹훅도 같은 역할이지만 유실될 수 있으므로 배치가 최종 방어선.
 *
 * 5분마다 실행.
 */
@Slf4j
@Component
public class ChargeReconcileScheduler {

    private final ChargeRequestRepository chargeRequestRepository;
    private final ChargeReconcileService chargeReconcileService;
    private final MeterRegistry meterRegistry;

    /**
     * 이 배치가 마지막으로 한 바퀴를 끝낸 시각(epoch seconds).
     *
     * <p>실패 카운터만으로는 <b>배치가 아예 돌지 않는 상태</b>를 알 수 없다. 예외 없이
     * 멈추면(스케줄러 스레드 고갈, Pod가 뜨긴 했는데 @Scheduled가 안 붙은 상태 등)
     * 실패 수가 0이라 오히려 건강해 보인다. 그런데 미결 충전은 계속 쌓인다.
     *
     * <p>마지막 성공 시각을 두면 "지금 시각 − 이 값"으로 정체를 판정할 수 있다.
     * 5분 주기이므로 15분을 넘기면 두 번 연속 건너뛴 것이다.
     *
     * <p>미결 건이 없어 조기 반환하는 경우도 <b>성공으로 친다.</b> 배치는 제 역할을
     * 다한 것이고, 여기서 갱신하지 않으면 한가한 시간대에 정체로 오인된다.
     */
    private final AtomicLong lastSuccessEpochSeconds = new AtomicLong();

    public ChargeReconcileScheduler(
            ChargeRequestRepository chargeRequestRepository,
            ChargeReconcileService chargeReconcileService,
            MeterRegistry meterRegistry) {
        this.chargeRequestRepository = chargeRequestRepository;
        this.chargeReconcileService = chargeReconcileService;
        this.meterRegistry = meterRegistry;
        // 기동 직후 값이 0이면 "1970년 이후 한 번도 성공 안 함"으로 읽혀 즉시
        // 정체 경보가 뜬다. 기동 시각으로 초기화해 첫 주기를 기다린다.
        this.lastSuccessEpochSeconds.set(Instant.now().getEpochSecond());
        meterRegistry.gauge(
                "openbake.payment.reconcile.last_success_timestamp_seconds", lastSuccessEpochSeconds);
    }

    @Scheduled(fixedRate = 5 * 60 * 1000)  // 5분마다
    public void reconcileInProgressCharges() {
        List<ChargeRequest> inProgressRequests = chargeRequestRepository.findByStatus(ChargeStatus.IN_PROGRESS);

        if (inProgressRequests.isEmpty()) {
            lastSuccessEpochSeconds.set(Instant.now().getEpochSecond());
            return;
        }

        log.info("[배치] 미결 충전 확인 시작 — {}건", inProgressRequests.size());

        for (ChargeRequest request : inProgressRequests) {
            try {
                chargeReconcileService.reconcile(request);
            } catch (Exception e) {
                // PG 조회 실패 시 해당 건만 스킵하고 다음 건 처리 계속.
                // 실패가 반복되면 IN_PROGRESS가 수렴하지 못하고 사용자 돈이 계속 묶인다.
                // chargeRequestId는 계속 늘어나는 값이라 label로 쓰지 않는다 —
                // 어느 건인지는 아래 로그에서 찾는다.
                meterRegistry.counter("openbake.payment.reconcile.failed").increment();
                log.warn("[배치] reconcile 실패 — chargeRequestId={}, error={}",
                        request.getId(), e.getMessage());
            }
        }

        lastSuccessEpochSeconds.set(Instant.now().getEpochSecond());
        log.info("[배치] 미결 충전 확인 완료");
    }
}
