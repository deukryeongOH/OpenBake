package com.openbake.drop.application;

import com.openbake.drop.application.cache.CachedDrop;
import com.openbake.drop.application.port.ProductPort;
import com.openbake.drop.application.port.StockReservationPort;
import com.openbake.drop.application.service.DropStockSyncService;
import com.openbake.drop.domain.repository.DropEntryRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class DropStockSyncServiceTest {

    @Mock
    private StockReservationPort stockReservationPort;

    @Mock
    private DropEntryRepository dropEntryRepository;

    @Mock
    private ProductPort productPort;

    @InjectMocks
    private DropStockSyncService dropStockSyncService;

    private final Long dropId = 1L;
    private final Long productId = 100L;

    private CachedDrop drop(LocalDateTime end) {
        return new CachedDrop(LocalDate.now(), dropId, productId,
                LocalDateTime.now().minusMinutes(10), end, new AtomicBoolean(true), new AtomicBoolean(false));
    }

    @Test
    @DisplayName("워밍업 초기값은 총 수량에서 이미 선점된 수량을 뺀 값이다")
    void warmUp_UsesTotalMinusReserved() {
        // given: 총 100개 중 30개가 이미 RESERVED
        CachedDrop cached = drop(LocalDateTime.now().plusMinutes(30));
        given(productPort.getTotalQuantity(productId)).willReturn(100);
        given(dropEntryRepository.sumReservedQuantity(dropId)).willReturn(30);

        // when
        dropStockSyncService.warmUp(cached);

        // then
        verify(stockReservationPort).initIfAbsent(eq(dropId), eq(70), any(Duration.class));
    }

    @Test
    @DisplayName("드롭 시작 시점에는 선점 합계가 0이라 총 수량 그대로 초기화된다")
    void warmUp_AtDropStart_UsesTotalQuantity() {
        // given
        CachedDrop cached = drop(LocalDateTime.now().plusMinutes(30));
        given(productPort.getTotalQuantity(productId)).willReturn(100);
        given(dropEntryRepository.sumReservedQuantity(dropId)).willReturn(0);

        // when
        dropStockSyncService.warmUp(cached);

        // then
        verify(stockReservationPort).initIfAbsent(eq(dropId), eq(100), any(Duration.class));
    }

    /**
     * TodayDropCache.refresh() 가 CachedDrop 을 새로 만들면서 tryMarkStarted 마킹을 초기화하므로
     * 진행 중인 드롭에도 워밍업이 다시 호출될 수 있다. 무조건 SET 이면 선점분이 되살아나 초과 판매가 된다.
     * 조건부 초기화(setIfAbsent)로 넘기는지 확인한다.
     */
    @Test
    @DisplayName("워밍업은 조건부라 이미 카운터가 있으면 덮어쓰지 않는다")
    void warmUp_IsConditional_DoesNotOverwriteExistingCounter() {
        // given: 카운터가 이미 있어 초기화가 건너뛰어진 상황
        CachedDrop cached = drop(LocalDateTime.now().plusMinutes(30));
        given(productPort.getTotalQuantity(productId)).willReturn(100);
        given(dropEntryRepository.sumReservedQuantity(dropId)).willReturn(30);
        given(stockReservationPort.initIfAbsent(any(), anyInt(), any())).willReturn(false);

        // when
        dropStockSyncService.warmUp(cached);

        // then: 조건부 API 만 쓰고, 값을 덮어쓰는 경로는 타지 않는다
        verify(stockReservationPort).initIfAbsent(eq(dropId), eq(70), any(Duration.class));
        verify(stockReservationPort, never()).clear(any());
    }

    @Test
    @DisplayName("TTL은 마감 시각 이후까지 남도록 여유를 둔다")
    void warmUp_TtlOutlivesDropEnd() {
        // given
        CachedDrop cached = drop(LocalDateTime.now().plusMinutes(30));
        given(productPort.getTotalQuantity(productId)).willReturn(100);
        given(dropEntryRepository.sumReservedQuantity(dropId)).willReturn(0);

        // when
        dropStockSyncService.warmUp(cached);

        // then
        ArgumentCaptor<Duration> ttl = ArgumentCaptor.forClass(Duration.class);
        verify(stockReservationPort).initIfAbsent(any(), anyInt(), ttl.capture());
        assertThat(ttl.getValue()).isGreaterThan(Duration.ofMinutes(30));
    }

    /**
     * TTL 은 dropEnd 에 여유(1시간)를 더해 계산하므로, 마감 직후에는 여전히 양수라 초기화가 일어난다.
     * 가드가 실제로 걸리는 것은 TTL 이 음수가 되는 시점(마감 + 여유가 모두 지난 경우)이다.
     * 실제로는 스케줄러가 마감 지난 드롭에 warmUp 을 호출하지 않으므로 방어적 가드다.
     */
    @Test
    @DisplayName("TTL이 이미 음수가 될 만큼 지난 드롭은 카운터를 초기화하지 않는다")
    void warmUp_LongEndedDrop_DoesNotInitialize() {
        // given: 마감 + TTL 여유(1시간)를 모두 넘긴 드롭
        CachedDrop cached = drop(LocalDateTime.now().minusHours(2));
        given(productPort.getTotalQuantity(productId)).willReturn(100);
        given(dropEntryRepository.sumReservedQuantity(dropId)).willReturn(0);

        // when
        dropStockSyncService.warmUp(cached);

        // then
        verify(stockReservationPort, never()).initIfAbsent(any(), anyInt(), any());
    }

    @Test
    @DisplayName("주기 동기화는 Redis 값을 DB에 절대값으로 반영한다")
    void sync_WritesRedisValueToDb() {
        // given
        CachedDrop cached = drop(LocalDateTime.now().plusMinutes(30));
        given(stockReservationPort.peek(dropId)).willReturn(42L);
        given(productPort.getTotalQuantity(productId)).willReturn(100);
        given(dropEntryRepository.sumReservedQuantity(dropId)).willReturn(58);

        // when
        dropStockSyncService.sync(cached);

        // then
        verify(productPort).syncRemainQuantity(productId, 42);
    }

    @Test
    @DisplayName("카운터가 아직 없으면 동기화하지 않는다")
    void sync_NoCounter_DoesNothing() {
        // given
        CachedDrop cached = drop(LocalDateTime.now().plusMinutes(30));
        given(stockReservationPort.peek(dropId)).willReturn(null);

        // when
        dropStockSyncService.sync(cached);

        // then
        verify(productPort, never()).syncRemainQuantity(any(), anyInt());
    }

    @Test
    @DisplayName("종료 시 최종값을 확정하고 카운터를 정리한다")
    void finalizeStock_SyncsAndClears() {
        // given
        CachedDrop cached = drop(LocalDateTime.now().minusMinutes(1));
        given(stockReservationPort.peek(dropId)).willReturn(7L);

        // when
        dropStockSyncService.finalizeStock(cached);

        // then
        verify(productPort).syncRemainQuantity(productId, 7);
        verify(stockReservationPort).clear(dropId);
    }

    @Test
    @DisplayName("종료 시 카운터가 이미 사라졌으면 drop_entry 합계로 확정한다")
    void finalizeStock_CounterMissing_FallsBackToDropEntry() {
        // given
        CachedDrop cached = drop(LocalDateTime.now().minusMinutes(1));
        given(stockReservationPort.peek(dropId)).willReturn(null);
        given(productPort.getTotalQuantity(productId)).willReturn(100);
        given(dropEntryRepository.sumReservedQuantity(dropId)).willReturn(95);

        // when
        dropStockSyncService.finalizeStock(cached);

        // then
        verify(productPort).syncRemainQuantity(productId, 5);
        verify(stockReservationPort).clear(dropId);
    }
}