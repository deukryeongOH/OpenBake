package com.openbake.product.infrastructure.elasticsearch;

import com.openbake.common.exception.BusinessException;
import com.openbake.common.exception.ErrorCode;
import com.openbake.product.application.port.ProductSearchPort;
import com.openbake.product.domain.Category;
import com.openbake.product.domain.Product;
import com.openbake.product.domain.ProductRepository;
import com.openbake.product.domain.Type;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 관리자 수동 트리거({@code reindexNowIfAvailable})가 스케줄 배치와 같은 락을 공유해
 * 동시 실행을 막는지 검증한다(docs/14). {@code @SchedulerLock}이 적용되는 {@code reindex()}
 * 자체는 ShedLock 라이브러리의 책임이라 여기서 다시 검증하지 않는다.
 */
@ExtendWith(MockitoExtension.class)
class ProductReindexSchedulerTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private ProductSearchPort productSearchPort;

    @Mock
    private LockProvider lockProvider;

    private ProductReindexScheduler scheduler;

    private final Long productId = 1L;

    @BeforeEach
    void setUp() {
        scheduler = new ProductReindexScheduler(productRepository, productSearchPort, lockProvider);
    }

    @Test
    @DisplayName("락을 잡을 수 있으면 재색인을 실행하고 끝나면 락을 해제한다")
    void reindexNowIfAvailable_LockAcquired_RunsAndUnlocks() {
        // given
        SimpleLock lock = mock(SimpleLock.class);
        given(lockProvider.lock(any(LockConfiguration.class))).willReturn(Optional.of(lock));

        Product product = mock(Product.class);
        given(product.getId()).willReturn(productId);
        given(productRepository.findAllByType(Type.GENERAL)).willReturn(List.of(product));
        given(productSearchPort.findAllIndexedIds()).willReturn(List.of(productId));

        // when
        ProductReindexScheduler.ReindexResult result = scheduler.reindexNowIfAvailable();

        // then
        assertThat(result.upsertCount()).isEqualTo(1);
        assertThat(result.orphanDeletedCount()).isEqualTo(0);
        verify(productSearchPort).indexAll(List.of(product));
        verify(lock).unlock();
    }

    @Test
    @DisplayName("다른 곳(예: 새벽 스케줄 배치)이 이미 실행 중이면 명확한 예외로 거부한다")
    void reindexNowIfAvailable_LockAlreadyHeld_Throws() {
        // given: 이미 다른 실행자가 락을 쥐고 있어 lock()이 빈 값을 반환한다
        given(lockProvider.lock(any(LockConfiguration.class))).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> scheduler.reindexNowIfAvailable())
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PRODUCT_REINDEX_ALREADY_RUNNING);

        verify(productRepository, never()).findAllByType(any());
        verify(productSearchPort, never()).indexAll(any());
    }

    @Test
    @DisplayName("재색인 도중 예외가 나도 락은 반드시 해제한다")
    void reindexNowIfAvailable_FailureDuringReindex_StillUnlocks() {
        // given
        SimpleLock lock = mock(SimpleLock.class);
        given(lockProvider.lock(any(LockConfiguration.class))).willReturn(Optional.of(lock));
        given(productRepository.findAllByType(Type.GENERAL)).willThrow(new RuntimeException("일시적 오류"));

        // when & then
        assertThatThrownBy(() -> scheduler.reindexNowIfAvailable())
                .isInstanceOf(RuntimeException.class);

        verify(lock).unlock();
    }

    @Test
    @DisplayName("삭제된 상품은 재색인 후에도 색인에 없다")
    void deletedProductStaysAbsentAfterReindex() {
        // given
        SimpleLock lock = mock(SimpleLock.class);
        given(lockProvider.lock(any(LockConfiguration.class))).willReturn(Optional.of(lock));

        Product selling = product(1L);
        Product deleted = product(2L);
        deleted.markDeleted();
        given(productRepository.findAllByType(Type.GENERAL)).willReturn(List.of(selling, deleted));
        given(productSearchPort.findAllIndexedIds()).willReturn(List.of(1L, 2L));

        // when
        ProductReindexScheduler.ReindexResult result = scheduler.reindexNowIfAvailable();

        // then
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Product>> indexedProducts = ArgumentCaptor.forClass(List.class);
        verify(productSearchPort).indexAll(indexedProducts.capture());
        assertThat(indexedProducts.getValue()).containsExactly(selling);
        verify(productSearchPort).deleteIndex(2L);
        assertThat(result).isEqualTo(new ProductReindexScheduler.ReindexResult(1, 1));
    }

    /**
     * 품절은 삭제가 아니다. 검색 쿼리가 status 로 거르므로 색인에 남겨도 결과는 같고,
     * 지웠다 다시 만드는 왕복을 피할 수 있다.
     */
    @Test
    @DisplayName("품절 상품은 재색인 후에도 색인에 남는다")
    void soldOutProductStaysIndexed() {
        // given
        SimpleLock lock = mock(SimpleLock.class);
        given(lockProvider.lock(any(LockConfiguration.class))).willReturn(Optional.of(lock));

        Product selling = product(1L);
        Product soldOut = product(2L);
        soldOut.markSoldOut();
        given(productRepository.findAllByType(Type.GENERAL)).willReturn(List.of(selling, soldOut));
        given(productSearchPort.findAllIndexedIds()).willReturn(List.of(1L, 2L));

        // when
        ProductReindexScheduler.ReindexResult result = scheduler.reindexNowIfAvailable();

        // then
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Product>> indexedProducts = ArgumentCaptor.forClass(List.class);
        verify(productSearchPort).indexAll(indexedProducts.capture());
        assertThat(indexedProducts.getValue()).containsExactly(selling, soldOut);
        verify(productSearchPort, never()).deleteIndex(2L);
        assertThat(result).isEqualTo(new ProductReindexScheduler.ReindexResult(2, 0));
    }

    private Product product(Long id) {
        Product product = Product.builder()
                .name("bread-" + id)
                .description("description")
                .imageUrl("image")
                .price(1_000)
                .sellerId(1L)
                .pickUpAvailableDates(Set.of(LocalDate.now().plusDays(1)))
                .category(Category.MEAL_BREADS)
                .type(Type.GENERAL)
                .build();
        ReflectionTestUtils.setField(product, "id", id);
        return product;
    }
}