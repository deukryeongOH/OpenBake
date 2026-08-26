package com.openbake.order.application;

import com.openbake.order.domain.Order;
import com.openbake.order.domain.OrderRepository;
import com.openbake.order.domain.OrderState;
import com.openbake.order.domain.SalesType;
import com.openbake.product.infrastructure.elasticsearch.ProductSearchRepository;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * gauge가 "존재하는지"가 아니라 "맞는 값을 세는지" 확인한다.
 *
 * 틀린 값이 대시보드에 뜨는 것은 지표가 없는 것보다 나쁘다. 특히 PENDING은
 * 돈이 묶인 주문 수라, 상태 필터나 시각 경계가 뒤집히면 조용히 거짓 안심을 준다.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
// 이 테스트만 별도 in-memory DB를 쓴다. 여러 @SpringBootTest 컨텍스트가 하나의
// jdbc:h2:mem:testdb를 공유하는데 ddl-auto가 create-drop이라, 컨텍스트가 하나 닫힐 때
// 아직 그 DB를 쓰는 다른 컨텍스트의 테이블까지 사라진다. DB를 분리하면 이 테스트가
// 공유 컨텍스트의 생성 시점을 앞당겨 다른 테스트를 깨뜨리는 일이 없다.
@TestPropertySource(properties =
        "spring.datasource.url=jdbc:h2:mem:order-metrics-test;DB_CLOSE_DELAY=-1")
class OrderMetricsRefresherIntegrationTest {

    @MockitoBean
    private ProductSearchRepository productSearchRepository;

    @MockitoBean
    private ElasticsearchOperations elasticsearchOperations;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderMetricsRefresher refresher;

    @Autowired
    private MeterRegistry meterRegistry;

    private static final BigDecimal AMOUNT = new BigDecimal("10000");

    private double gauge(String name) {
        return meterRegistry.get(name).gauge().value();
    }

    /** activeMemberId가 UNIQUE라서 주문마다 회원을 다르게 준다. */
    private Order pending(long memberId, LocalDateTime reservationExpiresAt) {
        return orderRepository.save(Order.createPending(
                memberId, "구매자" + memberId, SalesType.GENERAL, AMOUNT, reservationExpiresAt));
    }

    /**
     * 종료 상태인데 슬롯이 남은 주문. 정상 전이(markPaid 등)는 슬롯을 반납하므로
     * 이 상태는 전이 경로에 구멍이 있을 때만 생긴다. 그 버그 상태를 직접 만든다.
     */
    private void leakSlot(Order order, OrderState terminalState) {
        ReflectionTestUtils.setField(order, "orderState", terminalState);
        orderRepository.save(order);
    }

    @Test
    @DisplayName("만료된 PENDING만 센다 — 미래 예약분과 종료 상태는 제외한다")
    void countsOnlyExpiredPending() {
        LocalDateTime now = LocalDateTime.now();

        pending(9001L, now.minusMinutes(10));           // 만료됨 → 센다
        pending(9002L, now.minusMinutes(1));            // 만료됨 → 센다
        pending(9003L, now.plusMinutes(30));            // 아직 유효 → 제외
        leakSlot(pending(9004L, now.minusMinutes(10)), OrderState.PAID);      // 종료 상태 → 제외
        leakSlot(pending(9005L, now.minusMinutes(10)), OrderState.EXPIRED);   // 종료 상태 → 제외

        refresher.refresh();

        assertThat(gauge("openbake.order.pending.expired")).isEqualTo(2.0);
    }

    @Test
    @DisplayName("oldest age는 가장 오래 방치된 주문 기준이다 — 건수가 아니라 심각도를 본다")
    void oldestAgeUsesTheMostStaleOrder() {
        LocalDateTime now = LocalDateTime.now();

        pending(9101L, now.minusSeconds(120));
        pending(9102L, now.minusSeconds(600));   // 가장 오래됨
        pending(9103L, now.minusSeconds(300));

        refresher.refresh();

        // 초 단위 계산이라 테스트 실행 시간만큼의 오차를 허용한다.
        assertThat(gauge("openbake.order.pending.oldest_age_seconds"))
                .isBetween(600.0, 615.0);
    }

    @Test
    @DisplayName("만료된 PENDING이 없으면 age는 0이다 — 빈 결과가 예외가 되면 안 된다")
    void oldestAgeIsZeroWhenNothingIsOverdue() {
        pending(9201L, LocalDateTime.now().plusMinutes(30));

        refresher.refresh();

        assertThat(gauge("openbake.order.pending.expired")).isZero();
        assertThat(gauge("openbake.order.pending.oldest_age_seconds")).isZero();
    }

    @Test
    @DisplayName("슬롯 누수는 종료 상태에서 슬롯이 남은 주문만 센다 — 진행 중 주문은 정상이다")
    void countsOnlyLeakedSlots() {
        LocalDateTime now = LocalDateTime.now();

        pending(9301L, now.plusMinutes(30));                              // PENDING이 슬롯 보유 = 정상
        leakSlot(pending(9302L, now.plusMinutes(30)), OrderState.PAID);   // 누수 → 센다
        leakSlot(pending(9303L, now.plusMinutes(30)), OrderState.FAILED); // 누수 → 센다

        refresher.refresh();

        assertThat(gauge("openbake.order.active_slot.leaked")).isEqualTo(2.0);
    }

    @Test
    @DisplayName("정상 종료된 주문은 슬롯을 반납하므로 누수로 세지 않는다")
    void properlyClosedOrderIsNotLeaked() {
        Order order = pending(9401L, LocalDateTime.now().plusMinutes(30));
        order.markPaid();
        orderRepository.save(order);

        refresher.refresh();

        assertThat(gauge("openbake.order.active_slot.leaked")).isZero();
    }
}
