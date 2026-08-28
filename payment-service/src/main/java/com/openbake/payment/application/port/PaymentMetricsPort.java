package com.openbake.payment.application.port;

/**
 * 결제 Saga의 사고 신호를 기록하는 포트.
 *
 * <p>{@code docs/deep-dives/payment-saga-orchestration.md} 10장이 요구하는 항목 중
 * <b>발생 시점에만 알 수 있는 사건</b>을 센다. 미결 건수·최고령 나이처럼 주기적으로
 * 조회해 알 수 있는 값은 {@code PaymentMetricsRefresher}가 gauge로 채운다.
 *
 * <p>왜 포트로 두는가: 계측 지점이 application 계층에 있는데 Micrometer는 구현
 * 기술이다. {@code PgClient}와 같은 이유로 여기에 인터페이스만 두고 구현은
 * infrastructure에 둔다. 덕분에 단위 테스트에서 가짜 구현을 넣어 "이 경로가 정말
 * 계측되는가"를 검증할 수 있다.
 */
public interface PaymentMetricsPort {

    /** 환불이 실패로 기록됐다. 사용자 돈이 돌아가지 않았다는 뜻이다. */
    void refundFailed();

    /** 결제가 실패로 기록됐다. */
    void payFailed();

    /**
     * 멱등 레코드 생성이 유니크 제약에 걸려 재조회로 넘어갔다.
     *
     * <p>이것 자체는 정상 동작이다 — 같은 요청이 동시에 두 번 들어왔을 때 하나만
     * 살아남는 설계가 의도대로 작동한 것이다. 다만 <b>급증하면 클라이언트 재시도
     * 폭주나 멱등키 생성 규칙의 결함</b>을 뜻하므로 추이를 봐야 한다.
     *
     * @param operation {@code "pay"} 또는 {@code "refund"}
     */
    void idempotencyConflict(String operation);

    /**
     * 재조회까지 실패해 결과가 불확실하게 끝났다.
     *
     * <p>위 경합과 달리 이쪽은 정상 동작이 아니다. 두 번 시도해도 멱등 레코드를
     * 확보하지 못했다는 뜻이다.
     */
    void executionFailed();
}
