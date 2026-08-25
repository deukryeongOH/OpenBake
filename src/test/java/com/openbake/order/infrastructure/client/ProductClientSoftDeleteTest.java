package com.openbake.order.infrastructure.client;

import com.openbake.product.application.ProductService;
import com.openbake.product.domain.Category;
import com.openbake.product.domain.Product;
import com.openbake.product.domain.ProductInventoryRepository;
import com.openbake.product.domain.ProductRepository;
import com.openbake.product.domain.Type;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ProductClientSoftDeleteTest {

    @Test
    void deletedProductIsReturnedAsMissingSoOrderCannotProceed() {
        ProductService productService = mock(ProductService.class);
        ProductRepository productRepository = mock(ProductRepository.class);
        ProductInventoryRepository inventoryRepository = mock(ProductInventoryRepository.class);
        Product product = deletedProduct();
        when(productRepository.findById(10L)).thenReturn(Optional.of(product));

        var client = new ProductClient(productService, productRepository, inventoryRepository);

        assertThat(client.findProduct(10L)).isEmpty();
        verifyNoInteractions(productService, inventoryRepository);
    }

    private Product deletedProduct() {
        Product product = Product.builder()
                .name("식빵")
                .description("설명")
                .imageUrl("image")
                .price(5_000)
                .sellerId(1L)
                .pickUpAvailableDates(Set.of(LocalDate.now().plusDays(1)))
                .category(Category.MEAL_BREADS)
                .type(Type.GENERAL)
                .build();
        ReflectionTestUtils.setField(product, "id", 10L);
        product.markDeleted();
        return product;
    }
}
