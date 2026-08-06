package com.openbake.payment.presentation;

import com.openbake.payment.application.ChargeService;
import com.openbake.payment.domain.ChargeRequest;
import com.openbake.payment.domain.ChargeStatus;
import com.openbake.payment.domain.DepositAccount;
import com.openbake.payment.infrastructure.ChargeRequestJpaRepository;
import com.openbake.payment.infrastructure.DepositAccountJpaRepository;
import com.openbake.payment.infrastructure.WalletTransactionJpaRepository;
import com.openbake.payment.application.port.PgClient;
import com.openbake.payment.application.port.PgPaymentStatus;
import com.openbake.payment.presentation.dto.ChargeCreateResponse;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 웹훅 컨트롤러 테스트.
 *
 * @Transactional 없음 — 실제 트랜잭션 분리 검증.
 * PgClient를 Mock으로 대체해서 PG 조회 결과를 제어한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class WebhookControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ChargeService chargeService;

    @Autowired
    private ChargeRequestJpaRepository chargeRequestJpaRepository;

    @Autowired
    private DepositAccountJpaRepository depositAccountJpaRepository;

    @Autowired
    private WalletTransactionJpaRepository walletTransactionJpaRepository;

    @MockitoBean
    private PgClient pgClient;

    @Autowired
    private TransactionTemplate txTemplate;

    @Autowired
    private EntityManager em;

    private static final Long MEMBER_ID = 99901L;

    @AfterEach
    void cleanUp() {
        txTemplate.executeWithoutResult(status -> {
            em.createQuery("DELETE FROM WalletTransaction w WHERE w.depositAccount.memberId = :id")
                    .setParameter("id", MEMBER_ID).executeUpdate();
            em.createQuery("DELETE FROM ChargeRequest c WHERE c.memberId = :id")
                    .setParameter("id", MEMBER_ID).executeUpdate();
            em.createQuery("DELETE FROM DepositAccount d WHERE d.memberId = :id")
                    .setParameter("id", MEMBER_ID).executeUpdate();
        });
    }

    /**
     * 충전 요청을 생성하고 IN_PROGRESS로 전환한 ChargeRequest를 반환한다.
     */
    private ChargeRequest createInProgressRequest(String paymentKey) {
        depositAccountJpaRepository.save(DepositAccount.createMemberAccount(MEMBER_ID));
        ChargeCreateResponse created = chargeService.createChargeRequest(MEMBER_ID, new BigDecimal("10000"));
        return chargeService.markInProgress(created.pgOrderId(), paymentKey, MEMBER_ID, new BigDecimal("10000"));
    }

    @Test
    @DisplayName("위조 웹훅: 바디 status는 DONE인데 PG 조회는 미완료 → 잔액 변화 없음")
    void forgedWebhook_pgNotDone_noBalanceChange() throws Exception {
        // given — IN_PROGRESS 충전 요청
        ChargeRequest request = createInProgressRequest("pk_forged");

        // PG 조회 결과: 아직 미완료 (위조된 웹훅이라 실제로는 DONE이 아님)
        given(pgClient.getPaymentStatus(eq("pk_forged")))
                .willReturn(new PgPaymentStatus("pk_forged", "order1", "IN_PROGRESS", null));

        // when — 위조 웹훅 (body에 DONE이라고 적혀있지만 PG 조회로 판단)
        mockMvc.perform(post("/api/v1/webhooks/pg/toss")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"eventType":"PAYMENT_STATUS_CHANGED","data":{"paymentKey":"pk_forged","orderId":"order1","status":"DONE"}}
                                """))
                .andExpect(status().isOk());

        // then — 상태 변화 없음
        ChargeRequest updated = chargeRequestJpaRepository.findById(request.getId()).get();
        assertThat(updated.getStatus()).isEqualTo(ChargeStatus.IN_PROGRESS);

        // 잔액 변화 없음 (0원 그대로)
        DepositAccount account = depositAccountJpaRepository.findByMemberId(MEMBER_ID).get();
        assertThat(account.getBalance()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("정상 웹훅: PG 조회가 DONE → 잔액 증가, pgMethod가 null이 아님")
    void legitimateWebhook_pgDone_balanceIncreased() throws Exception {
        // given — IN_PROGRESS 충전 요청
        ChargeRequest request = createInProgressRequest("pk_legit");

        // PG 조회 결과: DONE (진짜 결제 완료)
        given(pgClient.getPaymentStatus(eq("pk_legit")))
                .willReturn(new PgPaymentStatus("pk_legit", "order2", "DONE", "카드"));

        // when
        mockMvc.perform(post("/api/v1/webhooks/pg/toss")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"eventType":"PAYMENT_STATUS_CHANGED","data":{"paymentKey":"pk_legit","orderId":"order2","status":"DONE"}}
                                """))
                .andExpect(status().isOk());

        // then — DONE 전이 + 잔액 증가
        ChargeRequest updated = chargeRequestJpaRepository.findById(request.getId()).get();
        assertThat(updated.getStatus()).isEqualTo(ChargeStatus.DONE);
        assertThat(updated.getPgMethod()).isEqualTo("카드");

        DepositAccount account = depositAccountJpaRepository.findByMemberId(MEMBER_ID).get();
        assertThat(account.getBalance()).isEqualByComparingTo("10000");
    }

    @Test
    @DisplayName("매칭되는 ChargeRequest 없음 → 200 반환, 예외 없음")
    void noMatchingChargeRequest_returns200() throws Exception {
        mockMvc.perform(post("/api/v1/webhooks/pg/toss")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"eventType":"PAYMENT_STATUS_CHANGED","data":{"paymentKey":"pk_unknown","orderId":"order3","status":"DONE"}}
                                """))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("PG 조회가 예외를 던짐 → 200 반환 (토스 재시도 방지)")
    void pgQueryThrows_returns200() throws Exception {
        // given — IN_PROGRESS 충전 요청
        createInProgressRequest("pk_error");

        // PG 조회 실패
        given(pgClient.getPaymentStatus(eq("pk_error")))
                .willThrow(new RuntimeException("PG 조회 타임아웃"));

        // when & then — 200 반환 (예외가 밖으로 나가지 않음)
        mockMvc.perform(post("/api/v1/webhooks/pg/toss")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"eventType":"PAYMENT_STATUS_CHANGED","data":{"paymentKey":"pk_error","orderId":"order4","status":"DONE"}}
                                """))
                .andExpect(status().isOk());
    }
}
