package com.openbake.settlement.application;

import com.openbake.common.exception.EntityNotFoundException;
import com.openbake.settlement.application.port.SellerSettlementAccountReader;
import com.openbake.settlement.application.port.SellerSettlementAccountSnapshot;
import com.openbake.settlement.domain.Settlement;
import com.openbake.settlement.domain.SettlementPayout;
import com.openbake.settlement.domain.SettlementPayoutRepository;
import com.openbake.settlement.domain.SettlementRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.DataIntegrityViolationException;
@Service
@RequiredArgsConstructor
@Transactional
public class SettlementPayoutService {

    private final SettlementRepository settlementRepository;
    private final SettlementPayoutRepository payoutRepository;
    private final SellerSettlementAccountReader
            sellerSettlementAccountReader;

    public SettlementPayoutResult start(
            Long settlementId,
            String idempotencyKey
    ) {
        validateSettlementId(settlementId);
        /** idempotencyKey는 Repository 조회 전에 검증하기 위함 */
        validateIdempotencyKey(idempotencyKey);

        SettlementPayout existing =
                payoutRepository
                        .findByIdempotencyKey(idempotencyKey)
                        .orElse(null);

        /*
         * 같은 요청이 다시 들어오면 신규 지급을 만들지 않고
         * 기존 결과를 반환합니다.
         */
        if (existing != null) {
            if (!existing.getSettlementId().equals(settlementId)) {
                throw new IllegalStateException(
                        "다른 정산에서 이미 사용된 멱등키입니다."
                );
            }

            return SettlementPayoutResult.from(existing);
        }

        Settlement settlement = findSettlement(settlementId);

        /**
         * 관리자 로그인 정보가 아니라,
         * 지급 대상 정산서의 sellerId로 조회합니다.
         */
        SellerSettlementAccountSnapshot account =
                sellerSettlementAccountReader.getAccount(
                        settlement.getSellerId()
                );

        settlement.startPaying();

        SettlementPayout payout =
                SettlementPayout.create(
                        settlement.getId(),
                        settlement.getSellerId(),
                        settlement.getPayoutAmount(),
                        idempotencyKey,
                        account.bankCode(),
                        account.accountNumber(),
                        account.accountHolder()
                );

        payout.startProcessing();

        /** 동시 중복 요청 예외 처리 */
        SettlementPayout saved;

        try {
            saved = payoutRepository.save(payout);
        } catch (DataIntegrityViolationException e) {
            SettlementPayout duplicated =
                    payoutRepository
                            .findByIdempotencyKey(idempotencyKey)
                            .orElseThrow(() -> e);

            if (!duplicated.getSettlementId().equals(settlementId)) {
                throw new IllegalStateException(
                        "다른 정산에서 이미 사용된 멱등키입니다."
                );
            }

            return SettlementPayoutResult.from(duplicated);
        }

        return SettlementPayoutResult.from(saved);
    }

    public SettlementPayoutResult complete(
            Long payoutId,
            String externalTransactionId
    ) {
        SettlementPayout payout = findPayout(payoutId);
        Settlement settlement =
                findSettlement(payout.getSettlementId());

        payout.complete(externalTransactionId);
        settlement.complete();

        return SettlementPayoutResult.from(payout);
    }

    public SettlementPayoutResult fail(
            Long payoutId,
            String failureReason
    ) {
        SettlementPayout payout = findPayout(payoutId);
        Settlement settlement =
                findSettlement(payout.getSettlementId());

        payout.fail(failureReason);
        settlement.failPayment();

        return SettlementPayoutResult.from(payout);
    }

    private Settlement findSettlement(
            Long settlementId
    ) {
        return settlementRepository.findById(settlementId)
                .orElseThrow(() ->
                        new EntityNotFoundException(
                                "정산 정보를 찾을 수 없습니다. "
                                        + "settlementId="
                                        + settlementId
                        )
                );
    }

    private SettlementPayout findPayout(
            Long payoutId
    ) {
        if (payoutId == null || payoutId <= 0) {
            throw new IllegalArgumentException(
                    "payoutId는 0보다 커야 합니다."
            );
        }

        return payoutRepository.findById(payoutId)
                .orElseThrow(() ->
                        new EntityNotFoundException(
                                "지급 이력을 찾을 수 없습니다. "
                                        + "payoutId="
                                        + payoutId
                        )
                );
    }

    private void validateSettlementId(
            Long settlementId
    ) {
        if (settlementId == null || settlementId <= 0) {
            throw new IllegalArgumentException(
                    "settlementId는 0보다 커야 합니다."
            );
        }
    }

    private void validateIdempotencyKey(
            String idempotencyKey
    ) {
        if (idempotencyKey == null
                || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException(
                    "멱등키는 필수입니다."
            );
        }

        if (idempotencyKey.length() > 100) {
            throw new IllegalArgumentException(
                    "멱등키는 100자 이하여야 합니다."
            );
        }
    }
}