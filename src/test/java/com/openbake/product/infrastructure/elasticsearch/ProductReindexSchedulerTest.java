package com.openbake.product.infrastructure.elasticsearch;

import com.openbake.product.application.port.ProductSearchPort;
import com.openbake.product.domain.Category;
import com.openbake.product.domain.Product;
import com.openbake.product.domain.ProductRepository;
import com.openbake.product.domain.Type;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProductReindexSchedulerTest {

    @Test
    void deletedProductStaysAbsentAfterReindex() {
        ProductRepository productRepository = mock(ProductRepository.class);
        ProductSearchPort productSearchPort = mock(ProductSearchPort.class);
        Product selling = product(1L);
        Product deleted = product(2L);
        deleted.markDeleted();
        when(productRepository.findAllByType(Type.GENERAL)).thenReturn(List.of(selling, deleted));
        when(productSearchPort.findAllIndexedIds()).thenReturn(List.of(1L, 2L));

        ProductReindexScheduler.ReindexResult result =
                new ProductReindexScheduler(productRepository, productSearchPort).reindexNow();

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
    void soldOutProductStaysIndexed() {
        ProductRepository productRepository = mock(ProductRepository.class);
        ProductSearchPort productSearchPort = mock(ProductSearchPort.class);
        Product selling = product(1L);
        Product soldOut = product(2L);
        soldOut.markSoldOut();
        when(productRepository.findAllByType(Type.GENERAL)).thenReturn(List.of(selling, soldOut));
        when(productSearchPort.findAllIndexedIds()).thenReturn(List.of(1L, 2L));

        ProductReindexScheduler.ReindexResult result =
                new ProductReindexScheduler(productRepository, productSearchPort).reindexNow();

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
