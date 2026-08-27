package com.openbake.drop.application;

import com.openbake.common.exception.BusinessException;
import com.openbake.common.exception.ErrorCode;
import com.openbake.drop.application.cache.CachedDrop;
import com.openbake.drop.application.cache.TodayDropCache;
import com.openbake.drop.application.port.CurrentMemberPort;
import com.openbake.drop.application.port.ProductPort;
import com.openbake.drop.application.port.StockReservationPort;
import com.openbake.drop.application.service.DropLockService;
import com.openbake.drop.application.service.DropService;
import com.openbake.drop.application.service.DropStockSyncService;
import com.openbake.drop.domain.DropStatus;
import com.openbake.drop.domain.EntryStatus;
import com.openbake.drop.domain.entity.Drop;
import com.openbake.drop.domain.entity.DropEntry;
import com.openbake.drop.domain.repository.DropEntryRepository;
import com.openbake.drop.domain.repository.DropRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

/**
 * finalizeStock을 dropEnd + 유예 시간 뒤로 미루도록 재설계한 이유가 된 불변식을 고정해 둔다.
 *
 * <p>{@code DropStockSyncService.finalizeStock}이 실행되고 나면(누가 언제 호출하든) Redis 재고
 * 키가 사라진다. 그 시점 이후에 그 드롭에 대한 {@code rollbackStock} 호출이 들어오면(예: 드롭
 * 종료 직전 lock-start만 하고 결제하지 않은 주문을 만료 배치가 되돌리려 할 때) 이미 사라진 키를
 * 보고 {@code STOCK_NOT_INITIALIZED}를 던진다. 이 예외는 호출자의 {@code @Transactional}을
 * 롤백시켜, 방금 성공한 {@code drop_entry} FAILED 전이까지 함께 취소된다 — 주문은 PENDING에
 * 남아 재시도되고, 재고 1개는 판매도 반환도 되지 않은 채 사라진다.
 *
 * <p>그래서 운영 코드는 finalizeStock을 dropEnd 직후가 아니라 주문 만료 처리가 끝날 유예
 * 시간(openbake.drop.stock-finalize-delay)이 지난 뒤에만 호출한다({@code DropScheduler.
 * finalizeStockAfterGracePeriod}). 이 테스트는 그 유예가 왜 필요한지 근거가 되는 낮은 수준의
 * 상호작용(finalizeStock 다음의 rollbackStock)만 고정해 둔 것이지, 스케줄 타이밍 자체를
 * 검증하지는 않는다.
 *
 * <p>실제 Redis Lua 스크립트 대신, 그 계약("키가 없으면 NOT_INITIALIZED")만 재현하는
 * 인메모리 가짜 {@link StockReservationPort}를 두 서비스가 공유하게 해서, 서비스 간
 * 실제 상호작용(같은 카운터를 보는 것)은 그대로 두고 Redis 자체의 정확성은 검증 범위에서 뺀다.
 */
@ExtendWith(MockitoExtension.class)
class DropStockFinalizeThenRollbackBugTest {

    private final FakeStockReservationPort stockReservationPort = new FakeStockReservationPort();

    @Mock
    private DropEntryRepository dropEntryRepository;

    @Mock
    private ProductPort productPort;

    @Mock
    private DropRepository dropRepository;

    @Mock
    private DropService dropService;

    @Mock
    private CurrentMemberPort currentMemberPort;

    @Mock
    private TodayDropCache todayDropCache;

    private final Long dropId = 1L;
    private final Long productId = 100L;
    private final Long memberId = 10L;

    private DropStockSyncService dropStockSyncService;
    private DropLockService dropLockService;

    @BeforeEach
    void setUp() {
        MeterRegistry meterRegistry = new SimpleMeterRegistry();
        dropStockSyncService = new DropStockSyncService(stockReservationPort, dropEntryRepository, productPort, dropService, meterRegistry);
        dropLockService = new DropLockService(dropRepository, dropService, dropEntryRepository, productPort,
                currentMemberPort, stockReservationPort, todayDropCache);
    }

    // Drop 생성자는 "미래 시각 + 고정 슬롯"만 허용한다. finalizeStock은 dropStart/dropEnd를 쓰지
    // 않으므로, 유효한 값으로 만든 뒤 id/productId만 리플렉션으로 맞춘다.
    private Drop dropEntity() {
        Drop entity = Drop.builder()
                .dropStatus(DropStatus.COMPLETED)
                .productId(productId)
                .limitQuantity(5)
                .dropStart(LocalDate.now().plusDays(1).atTime(9, 0))
                .dropEnd(LocalDate.now().plusDays(1).atTime(10, 0))
                .build();
        ReflectionTestUtils.setField(entity, "id", dropId);
        return entity;
    }

    @Test
    @DisplayName("드롭 종료로 재고 카운터가 정리된 뒤, 그 직전 선점을 되돌리려는 만료 배치가 STOCK_NOT_INITIALIZED로 실패한다")
    void rollbackAfterFinalize_Throws_StockNotInitialized() {
        // given: 드롭이 시작해 재고 카운터가 워밍업됐고(총 10개), 종료 직전 한 명이 1개를 선점했다
        stockReservationPort.initIfAbsent(dropId, 10, Duration.ofHours(1));
        stockReservationPort.reserve(dropId, 1); // remain: 9

        DropEntry reservedButUnpaidEntry = DropEntry.builder()
                .dropId(dropId).memberId(memberId).entryStatus(EntryStatus.RESERVED).build();
        ReflectionTestUtils.setField(reservedButUnpaidEntry, "selectQuantity", 1);

        // when: 재고 확정이 실행된다 — 운영 코드에서는 DropScheduler.finalizeStockAfterGracePeriod가
        // dropEnd로부터 유예 시간이 지난 뒤에 호출한다. 이 테스트는 "확정이 이미 끝났다"는
        // 사실 자체가 그 뒤의 rollbackStock에 어떤 영향을 주는지만 본다.
        given(dropService.markStockFinalized(dropId)).willReturn(true);
        given(productPort.getTotalQuantity(productId)).willReturn(10);
        given(dropEntryRepository.sumReservedQuantity(dropId)).willReturn(1);
        dropStockSyncService.finalizeStock(dropEntity());

        // then: 재고 카운터는 이제 없다 — clear()가 실행됐다
        assertThat(stockReservationPort.peek(dropId)).isNull();

        // given: 뒤늦게 만료 배치가 결제 안 된 그 선점을 되돌리려 한다
        CachedDrop endedDrop = new CachedDrop(LocalDate.now(), dropId, productId,
                LocalDateTime.now().minusHours(1), LocalDateTime.now().minusSeconds(1),
                new AtomicBoolean(true), new AtomicBoolean(true), 5,
                "두쫀쿠", "설명", "image.jpg", 8000, Set.of(LocalDate.now().plusDays(7)));
        given(dropEntryRepository.findByDropIdAndMemberId(dropId, memberId))
                .willReturn(Optional.of(reservedButUnpaidEntry));
        given(dropEntryRepository.fail(dropId, memberId)).willReturn(1); // DB 전이 자체는 성공한다
        given(todayDropCache.find(dropId)).willReturn(Optional.of(endedDrop));

        // then: 재고를 되돌리는 단계에서 실패한다. 실제 운영 코드에서는 이 예외가
        // OrderExpirationTransactions.restoreAndExpire의 @Transactional을 롤백시켜
        // 방금 성공한 dropEntryRepository.fail() 의 DB 변경도 함께 취소되고, 주문은 PENDING에 남는다.
        assertThatThrownBy(() -> dropLockService.rollbackStock(dropId, memberId))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.STOCK_NOT_INITIALIZED);
    }

    /** Redis Lua 스크립트의 계약만 재현하는 인메모리 가짜. 원자성은 단일 스레드 테스트라 문제되지 않는다. */
    private static class FakeStockReservationPort implements StockReservationPort {

        private final Map<Long, Long> counters = new ConcurrentHashMap<>();

        @Override
        public long reserve(Long dropId, int quantity) {
            Long remain = counters.get(dropId);
            if (remain == null) {
                return NOT_INITIALIZED;
            }
            if (remain < quantity) {
                return OUT_OF_STOCK;
            }
            long updated = remain - quantity;
            counters.put(dropId, updated);
            return updated;
        }

        @Override
        public long rollback(Long dropId, int quantity, int totalQuantity) {
            Long remain = counters.get(dropId);
            if (remain == null) {
                return NOT_INITIALIZED;
            }
            long updated = remain + quantity;
            if (updated > totalQuantity) {
                return OUT_OF_STOCK;
            }
            counters.put(dropId, updated);
            return updated;
        }

        @Override
        public boolean initIfAbsent(Long dropId, int remainQuantity, Duration ttl) {
            return counters.putIfAbsent(dropId, (long) remainQuantity) == null;
        }

        @Override
        public Long peek(Long dropId) {
            return counters.get(dropId);
        }

        @Override
        public void clear(Long dropId) {
            counters.remove(dropId);
        }
    }
}