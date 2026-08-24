package com.openbake.payment.application;

/**
 * Payment 애플리케이션 로직 실행 중 발생한 예외.
 * 이 예외가 조율자에게 보일 때는 executePay/executeRefund 트랜잭션이 이미 롤백된 뒤다.
 */
public class PaymentExecutionException extends RuntimeException {

    public PaymentExecutionException(Throwable cause) {
        super(cause.getMessage(), cause);
    }
}
