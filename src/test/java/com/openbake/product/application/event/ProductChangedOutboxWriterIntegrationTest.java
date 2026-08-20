package com.openbake.product.application.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.openbake.common.outbox.OutboxEventRepository;
import com.openbake.product.domain.Category;
import com.openbake.product.domain.Product;
import com.openbake.product.domain.Type;
import java.time.LocalDate;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@ActiveProfiles("test")
class ProductChangedOutboxWriterIntegrationTest {

    @Autowired
    private ProductChangedOutboxWriter outboxWriter;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @AfterEach
    void cleanUp() {
        outboxEventRepository.deleteAll();
    }

    @Test
    void outboxEventRollsBackWithProductChangeTransaction() {
        Product product = Product.builder()
                .name("통밀 식빵")
                .description("담백한 식사빵")
                .imageUrl("image")
                .price(5000)
                .sellerId(1L)
                .pickUpAvailableDates(Set.of(LocalDate.now().plusDays(1)))
                .category(Category.MEAL_BREADS)
                .type(Type.GENERAL)
                .build();
        ReflectionTestUtils.setField(product, "id", 10L);

        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
            outboxWriter.created(product);
            throw new IllegalStateException("force rollback");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(outboxEventRepository.count()).isZero();
    }
}
