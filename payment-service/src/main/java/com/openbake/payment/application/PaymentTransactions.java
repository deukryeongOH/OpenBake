package com.openbake.payment.application;

import com.openbake.common.exception.BusinessException;
import com.openbake.common.exception.ErrorCode;
import com.openbake.payment.application.dto.PaymentIdempotentResult;
import com.openbake.payment.domain.AccountType;
import com.openbake.payment.domain.DepositAccount;
import com.openbake.payment.domain.DepositAccountRepository;
import com.openbake.payment.domain.OrderPayment;
import com.openbake.payment.domain.OrderPaymentRepository;
import com.openbake.payment.domain.PaymentRecord;
import com.openbake.payment.domain.PaymentRecordRepository;
import com.openbake.payment.domain.PaymentStatus;
import com.openbake.payment.domain.ReferenceType;
import com.openbake.payment.domain.TransactionType;
import com.openbake.payment.domain.WalletTransaction;
import com.openbake.payment.domain.WalletTransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;

/** Payment DB에서 원자적으로 끝나야 하는 트랜잭션 조각들. */
@Component
@RequiredArgsConstructor
public class PaymentTransactions {

    private static final String ORDER_CLOSED_MESSAGE = "종료된 주문의 결제 요청입니다.";

    private final DepositAccountRepository depositAccountRepository;
    private final OrderPaymentRepository orderPaymentRepository;
    private final WalletTransactionRepository walletTransactionRepository;
    private final PaymentRecordRepository paymentRecordRepository;

    /** refund fence → 기존 주문 결제 → 차감 키 순서로 잠그고 결제를 실행한다. */
    @Transactional
    public PaymentIdempotentResult executePay(
            String idempotencyKey, Long orderId, Long memberId, BigDecimal amount) {
        String refundKey = refundKey(orderId);
        if (refundKey.equals(idempotencyKey)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "차감 키와 환불 키는 같을 수 없습니다.");
        }

        PaymentRecord fence = lockOrCreateFail(refundKey, orderId, memberId, amount, null);
        if (fence.isSuccess()) {
            recordClosedPayAttempt(idempotencyKey, orderId, memberId, amount);
            return PaymentIdempotentResult.fail(ORDER_CLOSED_MESSAGE);
        }

        Optional<OrderPayment> existingPayment = orderPaymentRepository.findByOrderId(orderId);
        if (existingPayment.isPresent()) {
            validateSamePayment(existingPayment.get(), memberId, amount);
            PaymentRecord replay = lockOrCreateSuccess(idempotencyKey, orderId, memberId, amount);
            return PaymentIdempotentResult.from(replay);
        }

        PaymentRecord record = lockOrCreateFail(idempotencyKey, orderId, memberId, amount, null);
        if (record.isSuccess()) {
            return PaymentIdempotentResult.from(record);
        }

        try {
            performPay(orderId, memberId, amount);
        } catch (BusinessException | DataIntegrityViolationException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new PaymentExecutionException(e);
        }

        record.markSuccess();
        return PaymentIdempotentResult.from(record);
    }

    /** executePay 롤백 완료 뒤, 다른 동시 SUCCESS를 덮지 않으면서 FAIL을 확정한다. */
    @Transactional
    public PaymentIdempotentResult recordPayFailure(
            String idempotencyKey,
            Long orderId,
            Long memberId,
            BigDecimal amount,
            String failReason) {
        Optional<PaymentRecord> existing =
                paymentRecordRepository.findByIdempotencyKeyForUpdate(idempotencyKey);
        if (existing.isPresent()) {
            PaymentRecord record = existing.get();
            validateSameRecord(record, orderId, memberId, amount);
            if (record.isSuccess()) {
                return PaymentIdempotentResult.from(record);
            }
            record.markFail(failReason);
            return PaymentIdempotentResult.from(record);
        }

        PaymentRecord failed = PaymentRecord.fail(
                idempotencyKey, orderId, memberId, amount, failReason);
        return PaymentIdempotentResult.from(paymentRecordRepository.saveAndFlush(failed));
    }

    /** 실제 환불과 결제 없음 no-op을 같은 fence 트랜잭션으로 처리한다. */
    @Transactional
    public PaymentIdempotentResult executeRefund(
            String idempotencyKey, Long orderId, Long memberId, BigDecimal amount) {
        String expectedKey = refundKey(orderId);
        if (!expectedKey.equals(idempotencyKey)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "유효하지 않은 주문 환불 키입니다.");
        }

        PaymentRecord fence = lockOrCreateFail(idempotencyKey, orderId, memberId, amount, null);
        if (fence.isSuccess()) {
            return PaymentIdempotentResult.from(fence);
        }

        Optional<OrderPayment> existingPayment = orderPaymentRepository.findByOrderId(orderId);
        if (existingPayment.isEmpty()) {
            //결제가 없으면 돈을 움직이지 않고 fence만 닫는다. 이후 늦은 pay가 차감되지 않는다.
            fence.markSuccess();
            return PaymentIdempotentResult.from(fence);
        }

        OrderPayment payment = existingPayment.get();
        validateSamePayment(payment, memberId, amount);
        if (payment.getStatus() == PaymentStatus.REFUNDED) {
            fence.markSuccess();
            return PaymentIdempotentResult.from(fence);
        }

        try {
            performRefund(payment);
        } catch (BusinessException | DataIntegrityViolationException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new PaymentExecutionException(e);
        }

        fence.markSuccess();
        return PaymentIdempotentResult.from(fence);
    }

    @Transactional
    public PaymentIdempotentResult recordRefundFailure(
            String idempotencyKey,
            Long orderId,
            Long memberId,
            BigDecimal amount,
            String failReason) {
        return recordPayFailure(idempotencyKey, orderId, memberId, amount, failReason);
    }

    @Transactional(readOnly = true)
    public PaymentIdempotentResult getPayResult(String idempotencyKey) {
        return paymentRecordRepository.findByIdempotencyKey(idempotencyKey)
                .map(PaymentIdempotentResult::from)
                .orElseGet(PaymentIdempotentResult::notFound);
    }

    // 기존 직접 호출 테스트와 내부 기능을 위한 원자적 결제 경계.
    @Transactional
    public void payDirect(Long orderId, Long memberId, BigDecimal amount) {
        performPay(orderId, memberId, amount);
    }

    @Transactional
    public void refundDirect(Long orderId) {
        OrderPayment payment = orderPaymentRepository.findByOrderId(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));
        performRefund(payment);
    }

    @Transactional
    public void confirmPayment(Long orderId) {
        OrderPayment payment = orderPaymentRepository.findByOrderId(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));
        payment.confirm();
    }

    private PaymentRecord lockOrCreateFail(
            String idempotencyKey,
            Long orderId,
            Long memberId,
            BigDecimal amount,
            String failReason) {
        return paymentRecordRepository.findByIdempotencyKeyForUpdate(idempotencyKey)
                .map(record -> {
                    validateSameRecord(record, orderId, memberId, amount);
                    return record;
                })
                .orElseGet(() -> paymentRecordRepository.saveAndFlush(
                        PaymentRecord.fail(idempotencyKey, orderId, memberId, amount, failReason)));
    }

    private PaymentRecord lockOrCreateSuccess(
            String idempotencyKey, Long orderId, Long memberId, BigDecimal amount) {
        Optional<PaymentRecord> existing =
                paymentRecordRepository.findByIdempotencyKeyForUpdate(idempotencyKey);
        if (existing.isPresent()) {
            PaymentRecord record = existing.get();
            validateSameRecord(record, orderId, memberId, amount);
            record.markSuccess();
            return record;
        }
        return paymentRecordRepository.saveAndFlush(
                PaymentRecord.success(idempotencyKey, orderId, memberId, amount));
    }

    private void recordClosedPayAttempt(
            String idempotencyKey, Long orderId, Long memberId, BigDecimal amount) {
        Optional<PaymentRecord> existing =
                paymentRecordRepository.findByIdempotencyKeyForUpdate(idempotencyKey);
        if (existing.isPresent()) {
            PaymentRecord record = existing.get();
            validateSameRecord(record, orderId, memberId, amount);
            record.markFail(ORDER_CLOSED_MESSAGE);
            return;
        }
        paymentRecordRepository.saveAndFlush(PaymentRecord.fail(
                idempotencyKey, orderId, memberId, amount, ORDER_CLOSED_MESSAGE));
    }

    private void validateSamePayment(
            OrderPayment payment, Long memberId, BigDecimal amount) {
        if (!payment.getMemberId().equals(memberId)
                || payment.getAmount().compareTo(amount) != 0) {
            throw new BusinessException(
                    ErrorCode.INVALID_PAYMENT_STATUS,
                    "기존 주문 결제의 회원 또는 금액이 요청과 다릅니다.");
        }
    }

    private void validateSameRecord(
            PaymentRecord record, Long orderId, Long memberId, BigDecimal amount) {
        if (!record.getOrderId().equals(orderId)
                || !record.getMemberId().equals(memberId)
                || record.getAmount().compareTo(amount) != 0) {
            throw new BusinessException(
                    ErrorCode.INVALID_INPUT,
                    "동일한 멱등키를 다른 주문, 회원 또는 금액으로 사용할 수 없습니다.");
        }
    }

    private void performPay(Long orderId, Long memberId, BigDecimal amount) {
        DepositAccount memberAccount = getOrCreateMemberAccount(memberId);
        DepositAccount platformAccount = getPlatformAccount();

        memberAccount.deduct(amount);

        OrderPayment payment = OrderPayment.create(orderId, memberId, amount);
        orderPaymentRepository.save(payment);

        walletTransactionRepository.save(WalletTransaction.create(
                memberAccount,
                TransactionType.PAYMENT,
                amount.negate(),
                memberAccount.getBalance(),
                ReferenceType.ORDER_PAYMENT,
                payment.getId()
        ));

        walletTransactionRepository.save(WalletTransaction.create(
                platformAccount,
                TransactionType.PAYMENT,
                amount,
                null,
                ReferenceType.ORDER_PAYMENT,
                payment.getId()
        ));
    }

    private void performRefund(OrderPayment payment) {
        payment.refund();

        DepositAccount memberAccount = depositAccountRepository
                .findByMemberIdForUpdate(payment.getMemberId())
                .orElseThrow(() -> new BusinessException(ErrorCode.DEPOSIT_ACCOUNT_NOT_FOUND));
        DepositAccount platformAccount = getPlatformAccount();

        memberAccount.refund(payment.getAmount());

        walletTransactionRepository.save(WalletTransaction.create(
                memberAccount,
                TransactionType.REFUND,
                payment.getAmount(),
                memberAccount.getBalance(),
                ReferenceType.ORDER_PAYMENT,
                payment.getId()
        ));

        walletTransactionRepository.save(WalletTransaction.create(
                platformAccount,
                TransactionType.REFUND,
                payment.getAmount().negate(),
                null,
                ReferenceType.ORDER_PAYMENT,
                payment.getId()
        ));
    }

    private DepositAccount getOrCreateMemberAccount(Long memberId) {
        return depositAccountRepository.findByMemberIdForUpdate(memberId)
                .orElseGet(() -> depositAccountRepository.save(
                        DepositAccount.createMemberAccount(memberId)));
    }

    private DepositAccount getPlatformAccount() {
        return depositAccountRepository.findByAccountType(AccountType.PLATFORM)
                .orElseThrow(() -> new BusinessException(ErrorCode.PLATFORM_ACCOUNT_NOT_FOUND));
    }

    private String refundKey(Long orderId) {
        return "order-" + orderId + "-refund";
    }
}
