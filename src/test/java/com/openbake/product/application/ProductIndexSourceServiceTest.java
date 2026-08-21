package com.openbake.product.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.openbake.product.domain.Category;
import com.openbake.product.domain.Product;
import com.openbake.product.domain.ProductRepository;
import com.openbake.product.domain.Type;
import java.time.LocalDate;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

class ProductIndexSourceServiceTest {

    @Test
    void returnsGeneralAndDropInForcedIdOrder() {
        ProductRepository repository = org.mockito.Mockito.mock(ProductRepository.class);
        Product general = product(Type.GENERAL, 1L);
        Product drop = product(Type.DROP, 2L);
        when(repository.findAll(any(Pageable.class)))
                .thenReturn(new PageImpl<>(java.util.List.of(general, drop)));

        var result = new ProductIndexSourceService(repository).findPage(0, 100);

        assertThat(result.getContent()).extracting("productId").containsExactly(1L, 2L);
        assertThat(result.getContent()).extracting("type").containsExactly("GENERAL", "DROP");
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).findAll(pageable.capture());
        assertThat(pageable.getValue().getSort().getOrderFor("id").isAscending()).isTrue();
    }

    private Product product(Type type, Long id) {
        Product product = Product.builder()
                .name("bread-" + id)
                .description("description")
                .imageUrl("image")
                .price(1000)
                .sellerId(1L)
                .pickUpAvailableDates(Set.of(LocalDate.now().plusDays(1)))
                .category(Category.MEAL_BREADS)
                .type(type)
                .build();
        ReflectionTestUtils.setField(product, "id", id);
        return product;
    }
}
