package com.openbake.product.application.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.openbake.common.outbox.OutboxEventRepository;
import com.openbake.product.domain.Category;
import com.openbake.product.domain.Product;
import com.openbake.product.domain.Type;
import com.openbake.interaction.application.InteractionOutboxWriter;
import java.time.LocalDate;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;
import com.openbake.product.infrastructure.elasticsearch.ProductSearchRepository;

@SpringBootTest
@ActiveProfiles("test")
class ProductChangedOutboxWriterIntegrationTest {

    // 이 테스트는 검색과 무관하지만 @SpringBootTest가 전체 컨텍스트를 올려 ES 빈까지 생성한다.
    // SimpleElasticsearchRepository는 생성 시점에 실제 접속을 시도하므로 로컬에 ES 없이도 통과하도록 대체한다.
    @MockitoBean
    private ProductSearchRepository productSearchRepository;

    @MockitoBean
    private ElasticsearchOperations elasticsearchOperations;

    @Autowired
    private ProductChangedOutboxWriter outboxWriter;

    @Autowired
    private InteractionOutboxWriter interactionOutboxWriter;

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

    @Test
    void cartAddOutboxRollsBackWithOwningTransaction() {
        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
            interactionOutboxWriter.cartAdded(1L, 2L, 3);
            throw new IllegalStateException("force cart rollback");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(outboxEventRepository.count()).isZero();
    }
}
