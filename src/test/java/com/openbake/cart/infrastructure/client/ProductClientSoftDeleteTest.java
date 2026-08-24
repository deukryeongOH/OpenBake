package com.openbake.cart.infrastructure.client;

import com.openbake.cart.application.CartService;
import com.openbake.cart.application.port.SellerPort;
import com.openbake.cart.domain.CartRepository;
import com.openbake.common.exception.BusinessException;
import com.openbake.common.exception.ErrorCode;
import com.openbake.interaction.application.InteractionOutboxWriter;
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

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ProductClientSoftDeleteTest {

    @Test
    void deletedProductIsReturnedAsMissingSoItCannotBeAddedToCart() {
        ProductRepository productRepository = mock(ProductRepository.class);
        ProductInventoryRepository inventoryRepository = mock(ProductInventoryRepository.class);
        CartRepository cartRepository = mock(CartRepository.class);
        SellerPort sellerPort = mock(SellerPort.class);
        InteractionOutboxWriter interactionOutboxWriter = mock(InteractionOutboxWriter.class);
        Product product = deletedProduct();
        when(productRepository.findById(10L)).thenReturn(Optional.of(product));

        var productClient = new ProductClient(productRepository, inventoryRepository);
        var cartService = new CartService(
                cartRepository, productClient, sellerPort, interactionOutboxWriter);

        assertThatThrownBy(() -> cartService.addItem(
                1L, 10L, 1, LocalDate.now().plusDays(1)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.PRODUCT_NOT_FOUND);
        verifyNoInteractions(inventoryRepository, cartRepository, sellerPort, interactionOutboxWriter);
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
