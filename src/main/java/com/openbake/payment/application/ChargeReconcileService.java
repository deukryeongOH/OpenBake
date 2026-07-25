package com.openbake.payment.application;

import com.openbake.payment.domain.ChargeRequest;
import com.openbake.payment.infrastructure.ChargeRequestRepository;
import com.openbake.payment.application.port.PgClient;
import com.openbake.payment.application.port.PgPaymentStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 충전 요청의 실제 PG 상태를 조회해서 반영하는 공통 로직.
 * 웹훅과 배치 스케줄러가 동일한 메서드를 호출한다.
 *
 * 웹훅 바디의 status를 신뢰하지 않는다.
 * 토스 결제 웹훅(PAYMENT_STATUS_CHANGED)에는 HMAC 서명도 secret도 없으므로,
 * 웹훅은 "이 결제를 확인해보라"는 신호로만 쓰고 실제 판단은 PG 조회로 한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChargeReconcileService {

    private final ChargeService chargeService;
    private final PgClient pgClient;
    private final ChargeRequestRepository chargeRequestRepository;

    /**
     * paymentKey로 충전 요청을 찾아 PG 조회 후 상태를 반영한다.
     * 웹훅 컨트롤러가 호출한다.
     *
     * 매칭되는 요청이 없거나 이미 완료된 건이면 무시한다.
     */
    public void reconcileByPaymentKey(String paymentKey) {
        ChargeRequest chargeRequest = chargeRequestRepository
                .findByPgPaymentKey(paymentKey)
                .orElse(null);

        if (chargeRequest == null) {
            log.warn("[reconcile] 매칭되는 충전 요청 없음: paymentKey={}", paymentKey);
            return;
        }

        if (chargeRequest.isDone()) {
            log.info("[reconcile] 이미 처리 완료된 건: chargeRequestId={}", chargeRequest.getId());
            return;
        }

        reconcile(chargeRequest);
    }

    /**
     * PG 조회 API로 실제 결제 상태를 확인하고 반영한다.
     *
     * @throws IllegalArgumentException pgPaymentKey가 null인 경우 (READY 상태 등)
     * @throws RuntimeException PG 조회 실패 시 — 호출자가 처리
     */
    public void reconcile(ChargeRequest request) {
        if (request.getPgPaymentKey() == null) {
            throw new IllegalArgumentException(
                    "pgPaymentKey가 null — PG 조회 불가. chargeRequestId=" + request.getId());
        }

        PgPaymentStatus status = pgClient.getPaymentStatus(request.getPgPaymentKey());

        if (status.isDone()) {
            chargeService.completeCharge(request, status.method());
            log.info("[reconcile] 충전 완료 처리 — chargeRequestId={}, method={}",
                    request.getId(), status.method());
        } else if (status.isFailed()) {
            chargeService.failCharge(request, status.status(), "PG 조회 결과: " + status.status());
            log.info("[reconcile] 충전 실패 처리 — chargeRequestId={}, status={}",
                    request.getId(), status.status());
        } else {
            log.debug("[reconcile] 미확정 상태 유지 — chargeRequestId={}, status={}",
                    request.getId(), status.status());
        }
    }
}
