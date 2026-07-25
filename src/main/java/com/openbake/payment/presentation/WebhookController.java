package com.openbake.payment.presentation;

import com.openbake.payment.application.ChargeReconcileService;
import com.openbake.payment.presentation.dto.TossWebhookRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 토스페이먼츠 웹훅 수신 컨트롤러.
 *
 * 웹훅 바디의 status를 신뢰하지 않는다.
 * 토스 결제 웹훅(PAYMENT_STATUS_CHANGED)에는 HMAC 서명도 secret도 없으므로,
 * paymentKey만 꺼내서 PG 조회 API로 실제 상태를 확인한다.
 *
 * 에러 응답 시 토스가 재시도를 반복하므로, 모든 예외를 잡아 200을 반환한다.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/webhooks/pg")
@RequiredArgsConstructor
public class WebhookController {

    private final ChargeReconcileService chargeReconcileService;

    @PostMapping("/toss")
    public ResponseEntity<Void> handleTossWebhook(@RequestBody TossWebhookRequest request) {
        log.info("[웹훅 수신] eventType={}, paymentKey={}",
                request.eventType(),
                request.data() != null ? request.data().paymentKey() : null);

        try {
            if (request.data() == null || request.data().paymentKey() == null) {
                log.warn("[웹훅] data 또는 paymentKey 없음 — 무시");
                return ResponseEntity.ok().build();
            }

            // PG 조회 API로 실제 상태 확인 후 처리
            chargeReconcileService.reconcileByPaymentKey(request.data().paymentKey());

        } catch (Exception e) {
            // 에러 응답 시 토스가 재시도를 반복하므로 예외를 삼키고 200 반환
            log.error("[웹훅] 처리 중 예외 발생", e);
        }

        return ResponseEntity.ok().build();
    }
}
