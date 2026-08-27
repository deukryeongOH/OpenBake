package com.openbake.product.application;

import com.openbake.common.exception.BusinessException;
import com.openbake.common.exception.ErrorCode;
import com.openbake.product.application.event.ProductChangedOutboxWriter;
import com.openbake.product.application.port.CurrentSellerPort;
import com.openbake.product.domain.Category;
import com.openbake.product.domain.Product;
import com.openbake.product.domain.ProductInventoryRepository;
import com.openbake.product.domain.ProductRepository;
import com.openbake.product.domain.ProductStatus;
import com.openbake.product.domain.Type;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ProductSoftDeleteTest {

    private static final long PRODUCT_ID = 10L;
    private static final long SELLER_ID = 1L;

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
    void deletedProductRemainsWithInventoryAndDetailReturnsNotFound() {
        Product product = product();
        given(currentSellerPort.getCurrentSellerId()).willReturn(SELLER_ID);
        given(productRepository.findById(PRODUCT_ID)).willReturn(Optional.of(product));

        productService.deleteGeneralProduct(PRODUCT_ID);

        assertThat(product.getStatus()).isEqualTo(ProductStatus.DELETED);
        verify(productRepository, never()).delete(product);
        verify(inventoryRepository, never()).delete(org.mockito.ArgumentMatchers.any());
        verify(outboxWriter).deleted(PRODUCT_ID);

        assertThatThrownBy(() -> productService.getGeneralProductInfo(PRODUCT_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.PRODUCT_NOT_FOUND);
    }

    private Product product() {
        Product product = Product.builder()
                .name("식빵")
                .description("설명")
                .imageUrl("image")
                .price(5_000)
                .sellerId(SELLER_ID)
                .pickUpAvailableDates(Set.of(LocalDate.now().plusDays(1)))
                .category(Category.MEAL_BREADS)
                .type(Type.GENERAL)
                .build();
        ReflectionTestUtils.setField(product, "id", PRODUCT_ID);
        return product;
    }
}
