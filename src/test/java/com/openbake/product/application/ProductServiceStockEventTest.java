package com.openbake.product.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;

import com.openbake.product.application.event.ProductChangedOutboxWriter;
import com.openbake.product.application.port.CurrentSellerPort;
import com.openbake.product.domain.Category;
import com.openbake.product.domain.Product;
import com.openbake.product.domain.ProductInventory;
import com.openbake.product.domain.ProductInventoryRepository;
import com.openbake.product.domain.ProductRepository;
import com.openbake.product.domain.Type;
import java.time.LocalDate;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class ProductServiceStockEventTest {

    @Mock
    private ProductRepository productRepository;
    @Mock
    private ProductInventoryRepository inventoryRepository;
    @Mock
    private CurrentSellerPort currentSellerPort;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @Mock
    private ProductChangedOutboxWriter outboxWriter;

    private ProductService productService;

    @BeforeEach
    void setUp() {
        productService = new ProductService(
                productRepository, inventoryRepository, currentSellerPort, eventPublisher, outboxWriter);
    }

    @Test
    void decreaseStockDoesNotCreateEmbeddingOutboxEvent() {
        given(inventoryRepository.decreaseStock(10L, 1)).willReturn(1);
        given(inventoryRepository.findByProductId(10L)).willReturn(inventory(4));

        int remain = productService.decreaseStock(10L, 1);

        assertThat(remain).isEqualTo(4);
        verifyNoInteractions(outboxWriter);
    }

    @Test
    void rollbackStockDoesNotCreateEmbeddingOutboxEvent() {
        given(inventoryRepository.rollbackStock(10L, 1)).willReturn(1);
        given(inventoryRepository.findByProductId(10L)).willReturn(inventory(5));
        given(productRepository.findById(10L)).willReturn(Optional.of(product()));

        int remain = productService.rollbackStock(10L, 1);

        assertThat(remain).isEqualTo(5);
        verifyNoInteractions(outboxWriter);
    }

    private ProductInventory inventory(int remain) {
        return ProductInventory.builder()
                .productId(10L)
                .remainQuantity(remain)
                .totalQuantity(5)
                .build();
    }

    private Product product() {
        return Product.builder()
                .name("식빵")
                .description("설명")
                .imageUrl("image")
                .price(5000)
                .sellerId(1L)
                .pickUpAvailableDates(Set.of(LocalDate.now().plusDays(1)))
                .category(Category.MEAL_BREADS)
                .type(Type.GENERAL)
                .build();
    }
}
