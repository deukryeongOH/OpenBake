package com.openbake.product.application.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.openbake.common.outbox.OutboxEvent;
import com.openbake.common.outbox.OutboxEventRepository;
import com.openbake.product.domain.Category;
import com.openbake.product.domain.Product;
import com.openbake.product.domain.Type;
import java.time.LocalDate;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class ProductChangedOutboxWriterTest {

    @Mock
    private OutboxEventRepository repository;

    @Test
    void created_usesSameEventIdInPayloadAndOutbox() {
        ObjectMapper objectMapper = JsonMapper.builder().findAndAddModules().build();
        ProductChangedOutboxWriter writer = new ProductChangedOutboxWriter(repository, objectMapper);
        Product product = Product.builder()
                .name("통밀 식빵")
                .description("담백한 식사빵")
                .imageUrl("image")
                .price(5000)
                .sellerId(1L)
                .pickUpAvailableDates(Set.of(LocalDate.now().plusDays(1)))
                .category(Category.MEAL_BREADS)
                .type(Type.DROP)
                .build();
        ReflectionTestUtils.setField(product, "id", 10L);
        given(repository.save(any())).willAnswer(invocation -> invocation.getArgument(0));

        writer.created(product);

        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(repository).save(captor.capture());
        OutboxEvent outbox = captor.getValue();
        ProductChangedEvent payload = objectMapper.readValue(outbox.getPayload(), ProductChangedEvent.class);
        assertThat(outbox.getEventId()).isEqualTo(payload.eventId().toString());
        assertThat(outbox.getTopic()).isEqualTo("product.changed.v1");
        assertThat(outbox.getEventKey()).isEqualTo("10");
        assertThat(payload.eventType()).isEqualTo(ProductChangedEventType.CREATED);
        assertThat(payload.category()).isEqualTo("MEAL_BREADS");
        assertThat(payload.type()).isEqualTo("DROP");
    }

    @Test
    void deleted_containsOnlyContractFieldsNeededForDeletion() {
        ObjectMapper objectMapper = JsonMapper.builder().findAndAddModules().build();
        ProductChangedOutboxWriter writer = new ProductChangedOutboxWriter(repository, objectMapper);
        given(repository.save(any())).willAnswer(invocation -> invocation.getArgument(0));

        writer.deleted(20L);

        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(repository).save(captor.capture());
        ProductChangedEvent payload = objectMapper.readValue(captor.getValue().getPayload(), ProductChangedEvent.class);
        assertThat(payload.eventType()).isEqualTo(ProductChangedEventType.DELETED);
        assertThat(payload.productId()).isEqualTo(20L);
        assertThat(payload.name()).isNull();
        assertThat(payload.description()).isNull();
        assertThat(payload.category()).isNull();
        assertThat(payload.type()).isNull();
    }
}
