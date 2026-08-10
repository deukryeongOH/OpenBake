package com.openbake.payment.application;

import com.openbake.common.exception.BusinessException;
import com.openbake.common.exception.ErrorCode;
import com.openbake.payment.domain.ChargeRequest;
import com.openbake.payment.domain.ChargeStatus;
import com.openbake.payment.domain.DepositAccount;
import com.openbake.payment.infrastructure.ChargeRequestJpaRepository;
import com.openbake.payment.infrastructure.DepositAccountJpaRepository;
import com.openbake.payment.infrastructure.WalletTransactionJpaRepository;
import com.openbake.payment.application.dto.ChargeCreateCommand;
import com.openbake.payment.application.dto.ChargeCreateResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ChargeServiceTest {

    @Autowired
    private ChargeService chargeService;

    @Autowired
    private ChargeRequestJpaRepository chargeRequestJpaRepository;

    @Autowired
    private DepositAccountJpaRepository depositAccountJpaRepository;

    @Autowired
    private WalletTransactionJpaRepository walletTransactionJpaRepository;

    @Nested
    @DisplayName("createChargeRequest()")
    class CreateChargeRequest {

        @Test
        @DisplayName("충전 요청이 READY 상태로 생성된다")
        void createsInReadyStatus() {
            ChargeCreateResult response = chargeService.createChargeRequest(new ChargeCreateCommand(1L, new BigDecimal("10000")));

            assertThat(response.amount()).isEqualByComparingTo("10000");
            assertThat(response.pgOrderId()).isNotBlank();

            // DB에서 확인
            ChargeRequest saved = chargeRequestJpaRepository.findById(response.chargeRequestId()).get();
            assertThat(saved.getStatus()).isEqualTo(ChargeStatus.READY);
            assertThat(saved.getMemberId()).isEqualTo(1L);
        }

        @Test
        @DisplayName("최소 금액 미만이면 예외가 발생한다")
        void failsWhenAmountBelowMinimum() {
            assertThatThrownBy(() ->
                    chargeService.createChargeRequest(new ChargeCreateCommand(1L, new BigDecimal("500")))
            ).isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.INVALID_CHARGE_AMOUNT);
        }

        @Test
        @DisplayName("최대 금액 초과면 예외가 발생한다")
        void failsWhenAmountAboveMaximum() {
            assertThatThrownBy(() ->
                    chargeService.createChargeRequest(new ChargeCreateCommand(1L, new BigDecimal("600000")))
            ).isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.INVALID_CHARGE_AMOUNT);
        }

        @Test
        @DisplayName("1,000원 단위가 아니면 예외가 발생한다")
        void failsWhenAmountNotInUnit() {
            assertThatThrownBy(() ->
                    chargeService.createChargeRequest(new ChargeCreateCommand(1L, new BigDecimal("1500")))
            ).isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.INVALID_CHARGE_AMOUNT);
        }

        @Test
        @DisplayName("기존 READY가 있으면 만료시키고 새 요청을 생성한다")
        void expiresExistingReadyAndCreatesNew() {
            ChargeCreateResult first = chargeService.createChargeRequest(new ChargeCreateCommand(1L, new BigDecimal("10000")));
            ChargeCreateResult second = chargeService.createChargeRequest(new ChargeCreateCommand(1L, new BigDecimal("20000")));

            // 기존 건은 EXPIRED
            ChargeRequest firstRequest = chargeRequestJpaRepository.findById(first.chargeRequestId()).get();
            assertThat(firstRequest.getStatus()).isEqualTo(ChargeStatus.EXPIRED);

            // 새 건은 READY, pgOrderId가 다름
            ChargeRequest secondRequest = chargeRequestJpaRepository.findById(second.chargeRequestId()).get();
            assertThat(secondRequest.getStatus()).isEqualTo(ChargeStatus.READY);
            assertThat(secondRequest.getPgOrderId()).isNotEqualTo(firstRequest.getPgOrderId());
        }

        @Test
        @DisplayName("IN_PROGRESS 건이 있어도 새 충전 요청이 생성된다")
        void allowsNewRequestWhenInProgressExists() {
            // given — 기존 요청을 IN_PROGRESS로 만듦
            ChargeCreateResult existing = chargeService.createChargeRequest(new ChargeCreateCommand(1L, new BigDecimal("10000")));
            ChargeRequest existingRequest = chargeRequestJpaRepository.findById(existing.chargeRequestId()).get();
            existingRequest.markInProgress("pk_existing");

            // when — 새 충전 요청 생성
            ChargeCreateResult newRequest = chargeService.createChargeRequest(new ChargeCreateCommand(1L, new BigDecimal("20000")));

            // then — 정상 생성됨
            assertThat(newRequest.chargeRequestId()).isNotEqualTo(existing.chargeRequestId());
            assertThat(newRequest.pgOrderId()).isNotEqualTo(existing.pgOrderId());
        }
    }

    @Nested
    @DisplayName("markInProgress()")
    class MarkInProgress {

        @Test
        @DisplayName("READY → IN_PROGRESS로 변경된다")
        void changesStatusToInProgress() {
            ChargeCreateResult created = chargeService.createChargeRequest(new ChargeCreateCommand(1L, new BigDecimal("10000")));

            ChargeRequest request = chargeService.markInProgress(
                    created.pgOrderId(), "payment_key_123", 1L, new BigDecimal("10000"));

            assertThat(request.getStatus()).isEqualTo(ChargeStatus.IN_PROGRESS);
            assertThat(request.getPgPaymentKey()).isEqualTo("payment_key_123");
        }

        @Test
        @DisplayName("본인 요청이 아니면 예외가 발생한다")
        void failsWhenNotOwner() {
            ChargeCreateResult created = chargeService.createChargeRequest(new ChargeCreateCommand(1L, new BigDecimal("10000")));

            assertThatThrownBy(() ->
                    chargeService.markInProgress(
                            created.pgOrderId(), "payment_key_123", 999L, new BigDecimal("10000"))
            ).isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.CHARGE_OWNER_MISMATCH);
        }

        @Test
        @DisplayName("금액이 불일치하면 예외가 발생한다")
        void failsWhenAmountMismatch() {
            ChargeCreateResult created = chargeService.createChargeRequest(new ChargeCreateCommand(1L, new BigDecimal("10000")));

            assertThatThrownBy(() ->
                    chargeService.markInProgress(
                            created.pgOrderId(), "payment_key_123", 1L, new BigDecimal("99999"))
            ).isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.CHARGE_AMOUNT_MISMATCH);
        }
    }

    @Nested
    @DisplayName("completeCharge()")
    class CompleteCharge {

        @Test
        @DisplayName("충전 완료 시 예치금이 증가하고 원장에 기록된다")
        void increasesBalanceAndRecordsTransaction() {
            // given — 충전 요청 생성 + IN_PROGRESS 전환
            ChargeCreateResult created = chargeService.createChargeRequest(new ChargeCreateCommand(1L, new BigDecimal("10000")));
            ChargeRequest request = chargeService.markInProgress(
                    created.pgOrderId(), "payment_key_123", 1L, new BigDecimal("10000"));

            // 회원 계좌 미리 생성 (잔액 5000원)
            DepositAccount account = DepositAccount.createMemberAccount(1L);
            account.charge(new BigDecimal("5000"));
            depositAccountJpaRepository.save(account);

            // when
            ChargeService.ChargeCompleteResult result = chargeService.completeCharge(request, "카드");

            // then — 잔액: 5000 + 10000 = 15000
            assertThat(result.balanceAfter()).isEqualByComparingTo("15000");
            assertThat(result.chargeRequest().getStatus()).isEqualTo(ChargeStatus.DONE);

            // 원장 기록 확인
            assertThat(walletTransactionJpaRepository.findAll()).hasSize(1);
        }

        @Test
        @DisplayName("계좌가 없으면 자동 생성 후 충전된다")
        void createsAccountIfNotExists() {
            ChargeCreateResult created = chargeService.createChargeRequest(new ChargeCreateCommand(2L, new BigDecimal("10000")));
            ChargeRequest request = chargeService.markInProgress(
                    created.pgOrderId(), "payment_key_456", 2L, new BigDecimal("10000"));

            ChargeService.ChargeCompleteResult result = chargeService.completeCharge(request, "카드");

            // 계좌 자동 생성 + 0 + 10000 = 10000
            assertThat(result.balanceAfter()).isEqualByComparingTo("10000");
            assertThat(depositAccountJpaRepository.findByMemberId(2L)).isPresent();
        }
    }

    @Nested
    @DisplayName("failCharge()")
    class FailCharge {

        @Test
        @DisplayName("실패 시 FAILED 상태로 변경되고 사유가 기록된다")
        void marksAsFailed() {
            ChargeCreateResult created = chargeService.createChargeRequest(new ChargeCreateCommand(1L, new BigDecimal("10000")));
            ChargeRequest request = chargeService.markInProgress(
                    created.pgOrderId(), "payment_key_123", 1L, new BigDecimal("10000"));

            chargeService.failCharge(request, "CARD_LIMIT_EXCEEDED", "카드 한도 초과");

            assertThat(request.getStatus()).isEqualTo(ChargeStatus.FAILED);
            assertThat(request.getFailureCode()).isEqualTo("CARD_LIMIT_EXCEEDED");
            assertThat(request.getFailureReason()).isEqualTo("카드 한도 초과");
        }
    }

    @Nested
    @DisplayName("expireStaleRequests()")
    class ExpireStaleRequests {

        @Test
        @DisplayName("만료 시간이 지나지 않은 READY 요청은 그대로 유지된다")
        void keepsNonExpiredRequests() {
            chargeService.createChargeRequest(new ChargeCreateCommand(1L, new BigDecimal("10000")));

            chargeService.expireStaleRequests();

            // 방금 만든 건 30분 안 지났으므로 READY 유지
            ChargeRequest request = chargeRequestJpaRepository.findAll().get(0);
            assertThat(request.getStatus()).isEqualTo(ChargeStatus.READY);
        }
    }
}
