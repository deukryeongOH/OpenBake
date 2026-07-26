package com.openbake.payment.application;

import com.openbake.common.exception.BusinessException;
import com.openbake.common.exception.ErrorCode;
import com.openbake.payment.domain.ChargeRequest;
import com.openbake.payment.domain.ChargeStatus;
import com.openbake.payment.domain.DepositAccount;
import com.openbake.payment.infrastructure.ChargeRequestRepository;
import com.openbake.payment.infrastructure.DepositAccountRepository;
import com.openbake.payment.infrastructure.WalletTransactionRepository;
import com.openbake.payment.application.port.PgApproveException;
import com.openbake.payment.application.port.PgApproveResponse;
import com.openbake.payment.application.port.PgClient;
import com.openbake.payment.application.port.PgUnknownResultException;
import com.openbake.payment.presentation.dto.ChargeApproveResponse;
import com.openbake.payment.presentation.dto.ChargeCreateResponse;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * ChargeFacade 테스트.
 *
 * @Transactional 없음 — 운영과 동일하게 각 ChargeService 메서드가
 * 독립적인 트랜잭션으로 커밋되는 환경에서 검증한다.
 * PgClient를 Mock으로 대체해서 실제 토스 API를 호출하지 않는다.
 */
@SpringBootTest
@ActiveProfiles("test")
// @Transactional 의도적으로 없음 — 운영과 동일한 트랜잭션 분리 환경
class ChargeFacadeTest {

    @Autowired
    private ChargeFacade chargeFacade;

    @Autowired
    private ChargeService chargeService;

    @MockitoBean
    private PgClient pgClient;

    @Autowired
    private ChargeRequestRepository chargeRequestRepository;

    @Autowired
    private DepositAccountRepository depositAccountRepository;

    @Autowired
    private WalletTransactionRepository walletTransactionRepository;

    @Autowired
    private TransactionTemplate txTemplate;

    @Autowired
    private EntityManager em;

    // memberId 충돌 방지 — 다른 테스트 클래스와 겹치지 않는 값
    private static final Long MEMBER_ID_SUCCESS = 88801L;
    private static final Long MEMBER_ID_FAIL = 88802L;
    private static final Long MEMBER_ID_CONCURRENT = 88803L;
    private static final Long MEMBER_ID_UNKNOWN = 88804L;

    private static final List<Long> MEMBER_IDS = List.of(
            MEMBER_ID_SUCCESS, MEMBER_ID_FAIL, MEMBER_ID_CONCURRENT, MEMBER_ID_UNKNOWN);

    @AfterEach
    void cleanUp() {
        txTemplate.executeWithoutResult(status -> {
            em.createQuery("DELETE FROM WalletTransaction w WHERE w.depositAccount.memberId IN :ids")
                    .setParameter("ids", MEMBER_IDS).executeUpdate();
            em.createQuery("DELETE FROM ChargeRequest c WHERE c.memberId IN :ids")
                    .setParameter("ids", MEMBER_IDS).executeUpdate();
            em.createQuery("DELETE FROM DepositAccount d WHERE d.memberId IN :ids")
                    .setParameter("ids", MEMBER_IDS).executeUpdate();
        });
    }

    @Test
    @DisplayName("PG 승인 성공 시 예치금이 증가하고 DONE 상태가 된다")
    void approveSuccess() {
        // given — 회원 계좌 (잔액 5000원)
        DepositAccount account = DepositAccount.createMemberAccount(MEMBER_ID_SUCCESS);
        account.charge(new BigDecimal("5000"));
        depositAccountRepository.save(account);

        ChargeCreateResponse created = chargeService.createChargeRequest(MEMBER_ID_SUCCESS, new BigDecimal("10000"));

        given(pgClient.approve(eq("payment_key_123"), eq(created.pgOrderId()), any()))
                .willReturn(new PgApproveResponse("payment_key_123", created.pgOrderId(), "카드", "DONE"));

        // when
        ChargeApproveResponse response = chargeFacade.approve(
                MEMBER_ID_SUCCESS, "payment_key_123", created.pgOrderId(), new BigDecimal("10000"));

        // then — 잔액: 5000 + 10000 = 15000
        assertThat(response.chargedAmount()).isEqualByComparingTo("10000");
        assertThat(response.balanceAfter()).isEqualByComparingTo("15000");

        ChargeRequest request = chargeRequestRepository.findById(created.chargeRequestId()).get();
        assertThat(request.getStatus()).isEqualTo(ChargeStatus.DONE);
    }

    @Test
    @DisplayName("PG 승인 실패 시 FAILED 상태가 되고 예외가 발생한다")
    void approveFailure() {
        // given
        depositAccountRepository.save(DepositAccount.createMemberAccount(MEMBER_ID_FAIL));
        ChargeCreateResponse created = chargeService.createChargeRequest(MEMBER_ID_FAIL, new BigDecimal("10000"));

        given(pgClient.approve(eq("fail_key"), eq(created.pgOrderId()), any()))
                .willThrow(new PgApproveException("CARD_LIMIT_EXCEEDED", "카드 한도 초과"));

        // when & then
        assertThatThrownBy(() ->
                chargeFacade.approve(MEMBER_ID_FAIL, "fail_key", created.pgOrderId(), new BigDecimal("10000"))
        ).isInstanceOf(PgApproveException.class);

        ChargeRequest request = chargeRequestRepository.findById(created.chargeRequestId()).get();
        assertThat(request.getStatus()).isEqualTo(ChargeStatus.FAILED);
        assertThat(request.getFailureCode()).isEqualTo("CARD_LIMIT_EXCEEDED");
    }

    @Test
    @DisplayName("PG 결과 모름 시 IN_PROGRESS 유지, 잔액 변화 없음")
    void approveUnknownResult() {
        // given — 회원 계좌 (잔액 5000원)
        DepositAccount account = DepositAccount.createMemberAccount(MEMBER_ID_UNKNOWN);
        account.charge(new BigDecimal("5000"));
        depositAccountRepository.save(account);

        ChargeCreateResponse created = chargeService.createChargeRequest(MEMBER_ID_UNKNOWN, new BigDecimal("10000"));

        given(pgClient.approve(eq("payment_key_timeout"), eq(created.pgOrderId()), any()))
                .willThrow(new PgUnknownResultException("네트워크 오류: Read timed out"));

        // when & then
        assertThatThrownBy(() ->
                chargeFacade.approve(MEMBER_ID_UNKNOWN, "payment_key_timeout", created.pgOrderId(), new BigDecimal("10000"))
        ).isInstanceOf(PgUnknownResultException.class);

        ChargeRequest request = chargeRequestRepository.findById(created.chargeRequestId()).get();
        assertThat(request.getStatus()).isEqualTo(ChargeStatus.IN_PROGRESS);

        DepositAccount updated = depositAccountRepository.findByMemberId(MEMBER_ID_UNKNOWN).get();
        assertThat(updated.getBalance()).isEqualByComparingTo("5000");
    }

    @Test
    @DisplayName("[동시성] 같은 pgOrderId로 동시 승인 요청 시 PG 호출은 1회만 발생한다")
    void concurrentApprove_onlyOnePgCall() throws Exception {
        // given — 회원 계좌 (잔액 0원) + 충전 요청
        depositAccountRepository.save(DepositAccount.createMemberAccount(MEMBER_ID_CONCURRENT));
        ChargeCreateResponse created = chargeService.createChargeRequest(
                MEMBER_ID_CONCURRENT, new BigDecimal("10000"));

        String pgOrderId = created.pgOrderId();
        String paymentKey = "concurrent_pk";

        // PG Mock 설정 — 스레드 시작 전에 완료
        given(pgClient.approve(eq(paymentKey), eq(pgOrderId), any()))
                .willReturn(new PgApproveResponse(paymentKey, pgOrderId, "카드", "DONE"));

        // CountDownLatch로 두 스레드를 동시에 출발시킴
        CountDownLatch readyLatch = new CountDownLatch(2);
        CountDownLatch startLatch = new CountDownLatch(1);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicReference<Exception> caughtException = new AtomicReference<>();

        Future<?> future1 = executor.submit(() -> {
            readyLatch.countDown();
            try {
                startLatch.await();
                chargeFacade.approve(MEMBER_ID_CONCURRENT, paymentKey, pgOrderId, new BigDecimal("10000"));
                successCount.incrementAndGet();
            } catch (Exception e) {
                caughtException.compareAndSet(null, e);
            }
        });

        Future<?> future2 = executor.submit(() -> {
            readyLatch.countDown();
            try {
                startLatch.await();
                chargeFacade.approve(MEMBER_ID_CONCURRENT, paymentKey, pgOrderId, new BigDecimal("10000"));
                successCount.incrementAndGet();
            } catch (Exception e) {
                caughtException.compareAndSet(null, e);
            }
        });

        readyLatch.await();
        startLatch.countDown();

        future1.get();
        future2.get();
        executor.shutdown();

        // then — 한 쪽만 성공, 다른 쪽은 CHARGE_NOT_APPROVABLE로 실패
        assertThat(successCount.get()).isEqualTo(1);

        assertThat(caughtException.get()).isInstanceOf(BusinessException.class);
        assertThat(((BusinessException) caughtException.get()).getErrorCode())
                .isEqualTo(ErrorCode.CHARGE_NOT_APPROVABLE);

        // 핵심: PG approve 호출이 정확히 1회 (락이 중복 PG 호출을 막았다)
        verify(pgClient, times(1)).approve(eq(paymentKey), eq(pgOrderId), any());

        // 잔액이 1회 충전분만 반영됨 (0 + 10000 = 10000)
        DepositAccount finalAccount = depositAccountRepository.findByMemberId(MEMBER_ID_CONCURRENT).get();
        assertThat(finalAccount.getBalance()).isEqualByComparingTo("10000");

        ChargeRequest request = chargeRequestRepository.findById(created.chargeRequestId()).get();
        assertThat(request.getStatus()).isEqualTo(ChargeStatus.DONE);
    }
}
