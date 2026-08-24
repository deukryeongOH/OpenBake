package com.openbake.payment.application;

import com.openbake.payment.application.dto.PaymentIdempotentResult;
import com.openbake.common.exception.BusinessException;
import com.openbake.common.exception.ErrorCode;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private static final String PAYMENT_EXECUTION_FAILED_MESSAGE = "결제 처리 중 오류가 발생했습니다.";
    private static final String REFUND_EXECUTION_FAILED_MESSAGE = "환불 처리 중 오류가 발생했습니다.";

    private final PaymentTransactions transactions;

    public PaymentIdempotentResult payIdempotent(
            String idempotencyKey,
            Long orderId,
            Long memberId,
            BigDecimal amount
    ) {
        validatePaymentRequest(idempotencyKey, orderId, memberId, amount);
        validatePayKey(idempotencyKey, orderId);
        try {
            return executePayWithConflictRetry(idempotencyKey, orderId, memberId, amount);
        } catch (BusinessException exception) {
            rethrowIfInvalidRequest(exception);
            return recordPayFailure(idempotencyKey, orderId, memberId, amount, exception.getMessage());
        } catch (PaymentExecutionException exception) {
            log.error("결제 실행 중 예기치 않은 오류가 발생했습니다. orderId={}, idempotencyKey={}",
                    orderId, idempotencyKey, exception);
            return recordPayFailure(
                    idempotencyKey, orderId, memberId, amount, PAYMENT_EXECUTION_FAILED_MESSAGE);
        }
    }

    public PaymentIdempotentResult refundIdempotent(
            String idempotencyKey,
            Long orderId,
            Long memberId,
            BigDecimal amount
    ) {
        validatePaymentRequest(idempotencyKey, orderId, memberId, amount);
        try {
            return executeRefundWithConflictRetry(idempotencyKey, orderId, memberId, amount);
        } catch (BusinessException exception) {
            rethrowIfInvalidRequest(exception);
            return recordRefundFailure(idempotencyKey, orderId, memberId, amount, exception.getMessage());
        } catch (PaymentExecutionException exception) {
            log.error("환불 실행 중 예기치 않은 오류가 발생했습니다. orderId={}, idempotencyKey={}",
                    orderId, idempotencyKey, exception);
            return recordRefundFailure(
                    idempotencyKey, orderId, memberId, amount, REFUND_EXECUTION_FAILED_MESSAGE);
        }
    }

    public PaymentIdempotentResult getPayResult(String idempotencyKey) {
        return transactions.getPayResult(idempotencyKey);
    }

    public void pay(Long orderId, Long memberId, BigDecimal amount) {
        transactions.payDirect(orderId, memberId, amount);
    }

    public void refund(Long orderId) {
        transactions.refundDirect(orderId);
    }

    public void confirmPayment(Long orderId) {
        transactions.confirmPayment(orderId);
    }

    private PaymentIdempotentResult executePayWithConflictRetry(
            String idempotencyKey,
            Long orderId,
            Long memberId,
            BigDecimal amount
    ) {
        try {
            return transactions.executePay(idempotencyKey, orderId, memberId, amount);
        } catch (DataIntegrityViolationException exception) {
            log.debug("결제 멱등 레코드 생성 경합을 재조회합니다. idempotencyKey={}", idempotencyKey);
            try {
                return transactions.executePay(idempotencyKey, orderId, memberId, amount);
            } catch (DataIntegrityViolationException repeatedException) {
                throw new PaymentExecutionException(repeatedException);
            }
        }
    }

    private PaymentIdempotentResult executeRefundWithConflictRetry(
            String idempotencyKey,
            Long orderId,
            Long memberId,
            BigDecimal amount
    ) {
        try {
            return transactions.executeRefund(idempotencyKey, orderId, memberId, amount);
        } catch (DataIntegrityViolationException exception) {
            log.debug("환불 멱등 레코드 생성 경합을 재조회합니다. idempotencyKey={}", idempotencyKey);
            try {
                return transactions.executeRefund(idempotencyKey, orderId, memberId, amount);
            } catch (DataIntegrityViolationException repeatedException) {
                throw new PaymentExecutionException(repeatedException);
            }
        }
    }

    private PaymentIdempotentResult recordPayFailure(
            String idempotencyKey,
            Long orderId,
            Long memberId,
            BigDecimal amount,
            String message
    ) {
        try {
            return transactions.recordPayFailure(idempotencyKey, orderId, memberId, amount, message);
        } catch (DataIntegrityViolationException exception) {
            return transactions.getPayResult(idempotencyKey);
        }
    }

    private PaymentIdempotentResult recordRefundFailure(
            String idempotencyKey,
            Long orderId,
            Long memberId,
            BigDecimal amount,
            String message
    ) {
        try {
            return transactions.recordRefundFailure(idempotencyKey, orderId, memberId, amount, message);
        } catch (DataIntegrityViolationException exception) {
            return transactions.getPayResult(idempotencyKey);
        }
    }

    private void rethrowIfInvalidRequest(BusinessException exception) {
        if (exception.getErrorCode() == ErrorCode.INVALID_INPUT
                || exception.getErrorCode() == ErrorCode.INVALID_PAYMENT_STATUS) {
            throw exception;
        }
    }

    private void validatePaymentRequest(
            String idempotencyKey, Long orderId, Long memberId, BigDecimal amount) {
        if (idempotencyKey == null || idempotencyKey.isBlank()
                || orderId == null || orderId <= 0
                || memberId == null || memberId <= 0
                || amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "유효하지 않은 결제 요청입니다.");
        }
    }

    private void validatePayKey(String idempotencyKey, Long orderId) {
        String prefix = "order-" + orderId + "-";
        String attempt = idempotencyKey.startsWith(prefix)
                ? idempotencyKey.substring(prefix.length())
                : "";
        if (attempt.length() < 2 || !attempt.chars().allMatch(Character::isDigit)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "유효하지 않은 주문 결제 멱등키입니다.");
        }
    }
}
