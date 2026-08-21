package com.openbake.interaction.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.openbake.interaction.application.ProductViewedEvent;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import tools.jackson.databind.json.JsonMapper;

class ProductViewedKafkaPublisherTest {

    @SuppressWarnings("unchecked")
    @Test
    void kafkaFailureIsCountedButNotPropagated() {
        KafkaTemplate<String, String> kafkaTemplate = org.mockito.Mockito.mock(KafkaTemplate.class);
        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("kafka down")));
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        ProductViewedKafkaPublisher publisher = new ProductViewedKafkaPublisher(
                kafkaTemplate, JsonMapper.builder().findAndAddModules().build(), registry);

        assertThatCode(() -> publisher.publish(
                new ProductViewedEvent(1L, 2L, null, Instant.now())))
                .doesNotThrowAnyException();
        assertThat(registry.counter(
                "openbake.interaction.publish.failures", "topic", "product.viewed.v1").count())
                .isEqualTo(1.0);
    }
}
