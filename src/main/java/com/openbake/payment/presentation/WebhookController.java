package com.openbake.payment.presentation;

import com.openbake.payment.application.ChargeReconcileService;
import com.openbake.payment.presentation.dto.TossWebhookRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Webhook", description = "PG 웹훅 수신")
@Slf4j
@RestController
@RequestMapping("/api/v1/webhooks/pg")
@RequiredArgsConstructor
public class WebhookController {

    private final ChargeReconcileService chargeReconcileService;

    @Operation(
            summary = "토스페이먼츠 웹훅 수신",
            description = "PG 결제 상태 변경 알림을 수신합니다. 인증 없이 호출되며, 어떤 경우에도 200을 반환합니다. 웹훅 바디의 status를 신뢰하지 않고 PG 조회 API로 실제 상태를 확인합니다."
    )
    @SecurityRequirements
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
