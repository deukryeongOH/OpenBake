//package com.openbake.settlement.domain;
//
//import org.junit.jupiter.api.DisplayName;
//import org.junit.jupiter.api.Test;
//
//import java.math.BigDecimal;
//
//import static org.assertj.core.api.Assertions.assertThat;
//import static org.assertj.core.api.Assertions.assertThatThrownBy;
//
//class SettlementPayoutTest {
//
//    @Test
//    @DisplayName("지급 원장을 생성하면 REQUESTED 상태가 된다")
//    void create() {
//        SettlementPayout payout = createPayout();
//
//        assertThat(payout.getSettlementId())
//                .isEqualTo(1L);
//
//        assertThat(payout.getSellerId())
//                .isEqualTo(10L);
//
//        assertThat(payout.getPayoutAmount())
//                .isEqualByComparingTo("45000.00");
//
//        assertThat(payout.getIdempotencyKey())
//                .isEqualTo("settlement-1-payout-1");
//
//        assertThat(payout.getStatus())
//                .isEqualTo(SettlementPayoutStatus.REQUESTED);
//
//        assertThat(payout.getRequestedAt())
//                .isNotNull();
//    }
//
//    @Test
//    @DisplayName("REQUESTED 상태의 지급을 처리 중 상태로 변경한다")
//    void startProcessing() {
//        SettlementPayout payout = createPayout();
//
//        payout.startProcessing();
//
//        assertThat(payout.getStatus())
//                .isEqualTo(SettlementPayoutStatus.PROCESSING);
//    }
//
//    @Test
//    @DisplayName("PROCESSING 상태의 지급을 완료한다")
//    void complete() {
//        SettlementPayout payout = createPayout();
//        payout.startProcessing();
//
//        payout.complete("bank-transfer-20260723-0001");
//
//        assertThat(payout.getStatus())
//                .isEqualTo(SettlementPayoutStatus.COMPLETED);
//
//        assertThat(payout.getExternalTransactionId())
//                .isEqualTo("bank-transfer-20260723-0001");
//
//        assertThat(payout.getCompletedAt())
//                .isNotNull();
//
//        assertThat(payout.getFailureReason())
//                .isNull();
//    }
//
//    @Test
//    @DisplayName("PROCESSING 상태의 지급을 실패 처리한다")
//    void fail() {
//        SettlementPayout payout = createPayout();
//        payout.startProcessing();
//
//        payout.fail("판매자 계좌 정보 오류");
//
//        assertThat(payout.getStatus())
//                .isEqualTo(SettlementPayoutStatus.FAILED);
//
//        assertThat(payout.getFailureReason())
//                .isEqualTo("판매자 계좌 정보 오류");
//
//        assertThat(payout.getFailedAt())
//                .isNotNull();
//    }
//
//    @Test
//    @DisplayName("REQUESTED가 아닌 상태에서는 지급 처리를 시작할 수 없다")
//    void rejectStartProcessingFromInvalidStatus() {
//        SettlementPayout payout = createPayout();
//        payout.startProcessing();
//
//        assertThatThrownBy(payout::startProcessing)
//                .isInstanceOf(IllegalStateException.class)
//                .hasMessageContaining(
//                        "처리 중 상태로 변경할 수 없는 지급 상태"
//                );
//    }
//
//    @Test
//    @DisplayName("PROCESSING 상태가 아니면 지급을 완료할 수 없다")
//    void rejectCompleteFromRequested() {
//        SettlementPayout payout = createPayout();
//
//        assertThatThrownBy(() ->
//                payout.complete("bank-transfer-1")
//        )
//                .isInstanceOf(IllegalStateException.class)
//                .hasMessageContaining(
//                        "완료 처리할 수 없는 지급 상태"
//                );
//    }
//
//    @Test
//    @DisplayName("외부 거래 ID는 필수이다")
//    void rejectBlankExternalTransactionId() {
//        SettlementPayout payout = createPayout();
//        payout.startProcessing();
//
//        assertThatThrownBy(() ->
//                payout.complete(" ")
//        )
//                .isInstanceOf(IllegalArgumentException.class)
//                .hasMessage("외부 거래 ID는 필수입니다.");
//    }
//
//    @Test
//    @DisplayName("PROCESSING 상태가 아니면 지급 실패 처리할 수 없다")
//    void rejectFailFromRequested() {
//        SettlementPayout payout = createPayout();
//
//        assertThatThrownBy(() ->
//                payout.fail("계좌 정보 오류")
//        )
//                .isInstanceOf(IllegalStateException.class)
//                .hasMessageContaining(
//                        "실패 처리할 수 없는 지급 상태"
//                );
//    }
//
//    @Test
//    @DisplayName("지급 실패 사유는 필수이다")
//    void rejectBlankFailureReason() {
//        SettlementPayout payout = createPayout();
//        payout.startProcessing();
//
//        assertThatThrownBy(() ->
//                payout.fail(" ")
//        )
//                .isInstanceOf(IllegalArgumentException.class)
//                .hasMessage("지급 실패 사유는 필수입니다.");
//    }
//
//    @Test
//    @DisplayName("멱등키는 필수이다")
//    void rejectBlankIdempotencyKey() {
//        assertThatThrownBy(() ->
//                SettlementPayout.create(
//                        1L,
//                        10L,
//                        new BigDecimal("45000.00"),
//                        " "
//                )
//        )
//                .isInstanceOf(IllegalArgumentException.class)
//                .hasMessage("멱등키는 필수입니다.");
//    }
//
//    private SettlementPayout createPayout() {
//        return SettlementPayout.create(
//                1L,
//                10L,
//                new BigDecimal("45000.00"),
//                "settlement-1-payout-1"
//        );
//    }
//}