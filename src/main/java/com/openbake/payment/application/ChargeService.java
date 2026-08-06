package com.openbake.payment.application;

import com.openbake.common.exception.BusinessException;
import com.openbake.common.exception.ErrorCode;
import com.openbake.payment.domain.ChargeRequest;
import com.openbake.payment.domain.ChargeStatus;
import com.openbake.payment.domain.DepositAccount;
import com.openbake.payment.domain.ReferenceType;
import com.openbake.payment.domain.TransactionType;
import com.openbake.payment.domain.WalletTransaction;
import com.openbake.payment.domain.ChargeRequestRepository;
import com.openbake.payment.domain.DepositAccountRepository;
import com.openbake.payment.domain.WalletTransactionRepository;
import com.openbake.payment.application.dto.ChargeCreateCommand;
import com.openbake.payment.application.dto.ChargeCreateResult;
import com.openbake.payment.application.dto.ChargeStatusResult;
import com.openbake.payment.application.dto.GetChargeStatusQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * 충전 비즈니스 로직.
 * 각 메서드는 독립적인 트랜잭션 단위다.
 * ChargeFacade가 이 메서드들을 조합해서 전체 충전 흐름을 만든다.
 */
@Service
@RequiredArgsConstructor
public class ChargeService {

    private final ChargeRequestRepository chargeRequestRepository;
    private final DepositAccountRepository depositAccountRepository;
    private final WalletTransactionRepository walletTransactionRepository;

    /**
     * [트랜잭션 1] 충전 요청 생성.
     * ChargeRequest를 READY 상태로 만들고, 프론트가 PG 결제창을 띄우는 데 필요한 정보를 반환한다.
     */
    @Transactional
    public ChargeCreateResult createChargeRequest(ChargeCreateCommand command) {
        Long memberId = command.memberId();
        BigDecimal amount = command.amount();

        // 기존 READY 건이 있으면 만료 처리 (결제창 안 끝낸 상태 — 돈 안 나감)
        // List로 받는 이유: 기존 existsBy 검사가 확인-후-저장이라 레이스가 있었고,
        // READY가 2건 이상 존재할 수 있음. Optional이면 IncorrectResultSizeDataAccessException.
        chargeRequestRepository.findByMemberIdAndStatus(memberId, ChargeStatus.READY)
                .forEach(ChargeRequest::markExpired);

        // IN_PROGRESS는 차단하지 않음.
        // 이중 적립 방어는 락 + isDone() 가드 + 원장 유니크가 담당하므로 이 검사와 무관.
        // IN_PROGRESS를 차단하면 배치가 못 푸는 건이 생겼을 때 회원이 영구히 충전 불가.

        // PG에 보낼 주문번호 생성 (UUID)
        String pgOrderId = UUID.randomUUID().toString();

        ChargeRequest request = ChargeRequest.create(memberId, amount, pgOrderId);
        chargeRequestRepository.save(request);

        String orderName = String.format("예치금 %,d원 충전", amount.intValue());

        return new ChargeCreateResult(
                request.getId(), pgOrderId, amount,
                orderName, request.getExpiresAt()
        );
    }

    /**
     * [트랜잭션 2-1] PG 승인 요청 전 상태 변경.
     * READY → IN_PROGRESS로 바꾸고 커밋한다.
     * 이후 PG API 호출은 트랜잭션 밖에서 한다.
     */
    @Transactional
    public ChargeRequest markInProgress(String pgOrderId, String pgPaymentKey, Long memberId, BigDecimal amount) {
        // 비관적 락으로 조회 — 더블클릭/네트워크 재시도로 같은 pgOrderId 요청이
        // 동시에 올 때, 두 번째 스레드는 첫 번째가 커밋할 때까지 대기한다.
        // 첫 번째가 IN_PROGRESS로 바꾸고 커밋하면, 두 번째는 READY가 아닌 걸 보고 예외.
        // → PG 승인 요청이 한 번만 나간다.
        // 락 순서: charge_requests → deposit_accounts. completeCharge()도 같은 순서. 뒤집으면 데드락.
        ChargeRequest request = chargeRequestRepository.findByPgOrderIdForUpdate(pgOrderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CHARGE_REQUEST_NOT_FOUND));

        // 본인 요청인지, 금액이 일치하는지 검증 (위변조 방지)
        request.validateOwner(memberId);
        request.validateAmountMatches(amount);
        request.markInProgress(pgPaymentKey);

        return request;
    }

    /**
     * [트랜잭션 2-2] PG 승인 성공 후 처리.
     * 예치금 증가 + 원장 기록 + ChargeRequest를 DONE으로 변경.
     */
    /**
     * 충전 완료 결과.
     * 퍼사드가 응답 DTO를 조립하는 데 필요한 정보를 담는다.
     */
    public record ChargeCompleteResult(BigDecimal balanceAfter, ChargeRequest chargeRequest) {}

    @Transactional
    public ChargeCompleteResult completeCharge(ChargeRequest request, String pgMethod) {
        // 비관적 락으로 조회 — 웹훅/배치 동시 호출 시 한 쪽이 대기
        ChargeRequest managed = chargeRequestRepository.findByIdForUpdate(request.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.CHARGE_REQUEST_NOT_FOUND));

        // 이미 처리된 건이면 스킵 (멱등성 — 락 획득 후 재확인)
        if (managed.isDone()) {
            BigDecimal balance = depositAccountRepository.findByMemberId(managed.getMemberId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.DEPOSIT_ACCOUNT_NOT_FOUND))
                    .getBalance();
            return new ChargeCompleteResult(balance, managed);
        }

        managed.markDone(pgMethod);

        // 회원 예치금 계좌에 금액 추가 — 비관적 락으로 Lost Update 방지
        DepositAccount account = depositAccountRepository.findByMemberIdForUpdate(managed.getMemberId())
                .orElseGet(() -> depositAccountRepository.save(
                        DepositAccount.createMemberAccount(managed.getMemberId())
                ));
        account.charge(managed.getAmount());

        // 원장에 충전 내역 기록
        walletTransactionRepository.save(WalletTransaction.create(
                account,
                TransactionType.CHARGE,
                managed.getAmount(),           // +금액 (돈 들어옴)
                account.getBalance(),          // 충전 후 잔액
                ReferenceType.CHARGE_REQUEST,
                managed.getId()
        ));

        return new ChargeCompleteResult(account.getBalance(), managed);
    }

    /**
     * [트랜잭션 2-2] PG 승인 실패 후 처리.
     * ChargeRequest를 FAILED로 변경하고 실패 사유를 기록한다.
     */
    @Transactional
    public void failCharge(ChargeRequest request, String failureCode, String failureReason) {
        // 비관적 락으로 조회 — completeCharge와 동시 실행 방지
        ChargeRequest managed = chargeRequestRepository.findByIdForUpdate(request.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.CHARGE_REQUEST_NOT_FOUND));

        // 이미 DONE이면 스킵 (배치/웹훅이 먼저 성공 처리한 경우)
        if (managed.isDone()) {
            return;
        }

        managed.markFailed(failureCode, failureReason);
    }

    /**
     * 충전 상태 조회 (5-5).
     * 프론트가 PG_TIMEOUT(504) 이후 폴링하거나, 충전 내역에서 상태를 확인할 때 사용.
     */
    @Transactional(readOnly = true)
    public ChargeStatusResult getChargeStatus(GetChargeStatusQuery query) {
        ChargeRequest request = chargeRequestRepository.findById(query.chargeRequestId())
                .orElseThrow(() -> new BusinessException(ErrorCode.CHARGE_REQUEST_NOT_FOUND));

        request.validateOwner(query.memberId());

        return new ChargeStatusResult(
                request.getId(),
                request.getAmount(),
                request.getStatus().name(),
                request.getPgMethod(),
                request.getFailureCode(),
                request.getFailureReason(),
                request.getRequestedAt(),
                request.getApprovedAt(),
                request.getExpiresAt()
        );
    }

    /**
     * 배치용 — 30분 초과된 READY 상태 충전 요청을 만료 처리.
     */
    @Transactional
    public void expireStaleRequests() {
        List<ChargeRequest> readyRequests = chargeRequestRepository.findByStatus(ChargeStatus.READY);
        for (ChargeRequest request : readyRequests) {
            if (request.isExpired()) {
                request.markExpired();
            }
        }
    }

}
