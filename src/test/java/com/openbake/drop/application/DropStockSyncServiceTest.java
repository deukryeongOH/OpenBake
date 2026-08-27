package com.openbake.drop.application;

import com.openbake.drop.application.cache.CachedDrop;
import com.openbake.drop.application.port.ProductPort;
import com.openbake.drop.application.port.StockReservationPort;
import com.openbake.drop.application.service.DropService;
import com.openbake.drop.application.service.DropStockSyncService;
import com.openbake.drop.domain.DropStatus;
import com.openbake.drop.domain.entity.Drop;
import com.openbake.drop.domain.repository.DropEntryRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.LocalDate;
import java.util.Set;
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

    @Mock
    private DropService dropService;

    @InjectMocks
    private DropStockSyncService dropStockSyncService;

    private final Long dropId = 1L;
    private final Long productId = 100L;
    private static final int LIMIT_QUANTITY = 5;

    private CachedDrop drop(LocalDateTime end) {
        return new CachedDrop(LocalDate.now(), dropId, productId,
                LocalDateTime.now().minusMinutes(10), end, new AtomicBoolean(true), new AtomicBoolean(false), LIMIT_QUANTITY,
                "두쫀쿠", "설명", "image.jpg", 8000, Set.of(LocalDate.now().plusDays(7)));
    }

    // Drop 생성자는 "미래 시각 + 고정 슬롯"만 허용하므로, 유효한 값으로 만든 뒤 리플렉션으로
    // dropId/productId만 맞춰 둔다. finalizeStock은 dropStart/dropEnd를 쓰지 않는다.
    private Drop dropEntity() {
        Drop entity = Drop.builder()
                .dropStatus(DropStatus.COMPLETED)
                .productId(productId)
                .limitQuantity(LIMIT_QUANTITY)
                .dropStart(LocalDate.now().plusDays(1).atTime(9, 0))
                .dropEnd(LocalDate.now().plusDays(1).atTime(10, 0))
                .build();
        ReflectionTestUtils.setField(entity, "id", dropId);
        return entity;
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

        // when
        dropStockSyncService.sync(cached);

        // then
        verify(productPort).syncRemainQuantity(productId, 42);
        // 대조 검사가 checkDrift로 빠졌으므로 주기 동기화에서는 DB를 읽지 않는다
        verify(productPort, never()).getTotalQuantity(any());
        verify(dropEntryRepository, never()).sumReservedQuantity(any());
    }

    @Test
    @DisplayName("드리프트 검사는 drop_entry 합계와 대조하되 Redis도 DB도 고치지 않는다")
    void checkDrift_ComparesWithoutCorrecting() {
        // given: Redis 42, drop_entry 기준 100-58=42 (일치)
        CachedDrop cached = drop(LocalDateTime.now().plusMinutes(30));
        given(stockReservationPort.peek(dropId)).willReturn(42L);
        given(productPort.getTotalQuantity(productId)).willReturn(100);
        given(dropEntryRepository.sumReservedQuantity(dropId)).willReturn(58);

        // when
        dropStockSyncService.checkDrift(cached);

        // then: 검출만 한다
        verify(dropEntryRepository).sumReservedQuantity(dropId);
        verify(productPort, never()).syncRemainQuantity(any(), anyInt());
        verify(stockReservationPort, never()).initIfAbsent(any(), anyInt(), any());
        verify(stockReservationPort, never()).clear(any());
    }

    @Test
    @DisplayName("불일치가 있어도 자동 보정하지 않는다")
    void checkDrift_OnMismatch_DoesNotCorrect() {
        // given: Redis 42, drop_entry 기준 100-50=50 (불일치)
        CachedDrop cached = drop(LocalDateTime.now().plusMinutes(30));
        given(stockReservationPort.peek(dropId)).willReturn(42L);
        given(productPort.getTotalQuantity(productId)).willReturn(100);
        given(dropEntryRepository.sumReservedQuantity(dropId)).willReturn(50);

        // when
        dropStockSyncService.checkDrift(cached);

        // then
        verify(productPort, never()).syncRemainQuantity(any(), anyInt());
        verify(stockReservationPort, never()).clear(any());
    }

    @Test
    @DisplayName("카운터가 아직 없으면 드리프트 검사도 집계 쿼리를 돌리지 않는다")
    void checkDrift_NoCounter_SkipsAggregate() {
        // given
        CachedDrop cached = drop(LocalDateTime.now().plusMinutes(30));
        given(stockReservationPort.peek(dropId)).willReturn(null);

        // when
        dropStockSyncService.checkDrift(cached);

        // then
        verify(dropEntryRepository, never()).sumReservedQuantity(any());
        verify(productPort, never()).getTotalQuantity(any());
    }

    /**
     * finalizeStock이 dropEnd + 유예 시간 뒤로 미뤄졌으므로(문서 12번), sync가 불리는
     * [dropStart, dropEnd] 구간 안에서 카운터가 없다는 건 "아직 초기화 전"이거나 "진행 중
     * Redis 유실" 둘 중 하나일 수밖에 없다. 두 경우 모두 재워밍업으로 복구돼야 한다.
     */
    @Test
    @DisplayName("카운터가 없으면(진행 중 Redis 유실 포함) 재워밍업을 시도한다")
    void sync_NoCounter_ReWarmsUp() {
        // given
        CachedDrop cached = drop(LocalDateTime.now().plusMinutes(30));
        given(stockReservationPort.peek(dropId)).willReturn(null);
        given(productPort.getTotalQuantity(productId)).willReturn(100);
        given(dropEntryRepository.sumReservedQuantity(dropId)).willReturn(30);

        // when
        dropStockSyncService.sync(cached);

        // then: drop_entry 합계(100-30=70)로 재워밍업하고, 이번 tick에는 DB 동기화를 하지 않는다
        // (재워밍업으로 값이 채워지면 다음 tick의 sync가 그 값을 DB에 반영한다)
        verify(stockReservationPort).initIfAbsent(eq(dropId), eq(70), any(Duration.class));
        verify(productPort, never()).syncRemainQuantity(any(), anyInt());
    }

    @Test
    @DisplayName("재워밍업 시도 중 카운터가 이미 있으면(경합) 덮어쓰지 않는다")
    void sync_NoCounter_ReWarmUp_IsConditional() {
        // given: peek은 null이었지만 그 사이 다른 스레드/틱이 먼저 초기화를 마쳤다
        CachedDrop cached = drop(LocalDateTime.now().plusMinutes(30));
        given(stockReservationPort.peek(dropId)).willReturn(null);
        given(productPort.getTotalQuantity(productId)).willReturn(100);
        given(dropEntryRepository.sumReservedQuantity(dropId)).willReturn(30);
        given(stockReservationPort.initIfAbsent(any(), anyInt(), any())).willReturn(false);

        // when
        dropStockSyncService.sync(cached);

        // then: 조건부 API(initIfAbsent)만 쓰고, 덮어쓰는 경로는 타지 않는다
        verify(stockReservationPort).initIfAbsent(eq(dropId), eq(70), any(Duration.class));
        verify(stockReservationPort, never()).clear(any());
    }

    @Test
    @DisplayName("확정 게이트를 통과하면 drop_entry 합계로 최종값을 확정하고 카운터를 정리한다")
    void finalizeStock_GatePasses_SyncsFromDropEntryAndClears() {
        // given
        Drop entity = dropEntity();
        given(dropService.markStockFinalized(dropId)).willReturn(true);
        given(productPort.getTotalQuantity(productId)).willReturn(100);
        given(dropEntryRepository.sumReservedQuantity(dropId)).willReturn(95);

        // when
        dropStockSyncService.finalizeStock(entity);

        // then: Redis peek()이 아니라 drop_entry 기준(100-95=5)이다 — 여러 인스턴스가
        // 각자 다른 Redis 스냅샷을 최종값으로 덮어쓰는 경합을 없애기 위함(문서 참고)
        verify(productPort).syncRemainQuantity(productId, 5);
        verify(stockReservationPort).clear(dropId);
        verify(stockReservationPort, never()).peek(any());
    }

    @Test
    @DisplayName("이미 확정된 드롭이면(게이트 실패) 아무 것도 하지 않는다 — 여러 인스턴스의 중복 실행 방지")
    void finalizeStock_AlreadyFinalized_DoesNothing() {
        // given
        Drop entity = dropEntity();
        given(dropService.markStockFinalized(dropId)).willReturn(false);

        // when
        dropStockSyncService.finalizeStock(entity);

        // then
        verify(productPort, never()).syncRemainQuantity(any(), anyInt());
        verify(stockReservationPort, never()).clear(any());
    }
}