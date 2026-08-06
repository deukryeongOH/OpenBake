package com.openbake.payment.application;

import com.openbake.payment.domain.ChargeRequest;
import com.openbake.payment.domain.ChargeStatus;
import com.openbake.payment.domain.ChargeRequestRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

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
@RequiredArgsConstructor
public class ChargeReconcileScheduler {

    private final ChargeRequestRepository chargeRequestRepository;
    private final ChargeReconcileService chargeReconcileService;

    @Scheduled(fixedRate = 5 * 60 * 1000)  // 5분마다
    public void reconcileInProgressCharges() {
        List<ChargeRequest> inProgressRequests = chargeRequestRepository.findByStatus(ChargeStatus.IN_PROGRESS);

        if (inProgressRequests.isEmpty()) {
            return;
        }

        log.info("[배치] 미결 충전 확인 시작 — {}건", inProgressRequests.size());

        for (ChargeRequest request : inProgressRequests) {
            try {
                chargeReconcileService.reconcile(request);
            } catch (Exception e) {
                // PG 조회 실패 시 해당 건만 스킵하고 다음 건 처리 계속
                log.warn("[배치] reconcile 실패 — chargeRequestId={}, error={}",
                        request.getId(), e.getMessage());
            }
        }

        log.info("[배치] 미결 충전 확인 완료");
    }
}
