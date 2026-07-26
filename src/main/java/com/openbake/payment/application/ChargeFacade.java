package com.openbake.payment.application;

import com.openbake.payment.domain.ChargeRequest;
import com.openbake.payment.application.port.PgApproveException;
import com.openbake.payment.application.port.PgApproveResponse;
import com.openbake.payment.application.port.PgClient;
import com.openbake.payment.application.port.PgUnknownResultException;
import com.openbake.payment.presentation.dto.ChargeApproveResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * 충전 승인 퍼사드 — 트랜잭션을 분리해서 PG 호출을 조율한다. (ARD-002)
 *
 * 왜 퍼사드인가?
 * PG API 호출은 외부 네트워크 통신이라 DB 트랜잭션 안에 넣으면 안 된다.
 * - 커넥션 풀 고갈: PG 응답이 3초 걸리면 DB 커넥션도 3초 잡고 있음
 * - 롤백의 착각: DB는 롤백해도 PG의 카드 승인은 되돌릴 수 없음
 *
 * 그래서 이렇게 나눈다:
 * [트랜잭션 1] READY → IN_PROGRESS 기록 + 커밋
 * [트랜잭션 밖] PG API 호출
 * [트랜잭션 2] 결과에 따라 DONE 또는 FAILED 기록
 *
 * 이 클래스 자체에는 @Transactional이 없다. 각 단계의 트랜잭션은 ChargeService가 관리한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChargeFacade {

    private final ChargeService chargeService;
    private final PgClient pgClient;

    /**
     * 충전 승인 처리.
     * 프론트가 토스 결제창 완료 후 보내는 요청을 받아서 PG 승인까지 처리한다.
     */
    public ChargeApproveResponse approve(Long memberId, String pgPaymentKey, String pgOrderId, BigDecimal amount) {

        // [트랜잭션 1] 상태를 IN_PROGRESS로 바꾸고 커밋
        ChargeRequest request = chargeService.markInProgress(pgOrderId, pgPaymentKey, memberId, amount);

        try {
            // [트랜잭션 밖] PG 승인 API 호출 — 네트워크 통신
            PgApproveResponse pgResponse = pgClient.approve(pgPaymentKey, pgOrderId, amount);

            // [트랜잭션 2] 성공 — 예치금 증가 + 원장 기록 + DONE
            ChargeService.ChargeCompleteResult result = chargeService.completeCharge(request, pgResponse.method());

            return new ChargeApproveResponse(
                    request.getId(),
                    result.chargeRequest().getStatus().name(),
                    amount,
                    result.balanceAfter(),
                    result.chargeRequest().getPgMethod(),
                    result.chargeRequest().getApprovedAt()
            );

        } catch (PgApproveException e) {
            // [트랜잭션 2] 확정 실패 — FAILED 기록
            chargeService.failCharge(request, e.getFailureCode(), e.getFailureReason());
            throw e;
        } catch (PgUnknownResultException e) {
            // 결과 모름 — IN_PROGRESS 유지. failCharge() 호출 안 함.
            // 배치(ChargeReconcileScheduler)가 다음 주기에 PG 조회해서 실제 결과를 반영한다.
            log.warn("[충전] PG 결과 모름 — chargeRequestId={}, reason={}",
                    request.getId(), e.getReason());
            throw e;
        }
    }
}
