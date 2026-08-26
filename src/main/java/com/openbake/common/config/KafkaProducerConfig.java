package com.openbake.common.config;

import java.util.Map;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.springframework.boot.kafka.autoconfigure.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.MicrometerProducerListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;

@Configuration
public class KafkaProducerConfig {

    /**
     * application.yml의 spring.kafka.producer.* (acks 등)를 그대로 반영하기 위해
     * KafkaProperties에서 프로퍼티를 빌드해서 쓴다. enable.idempotence는 명시적으로 켜서
     * producer 재시도로 인한 브로커 측 중복 적재를 Kafka 클라이언트 레벨에서 막는다.
     */
    @Bean
    public ProducerFactory<String, String> producerFactory(
            KafkaProperties kafkaProperties, MeterRegistry meterRegistry) {
        Map<String, Object> props = kafkaProperties.buildProducerProperties();
        props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        DefaultKafkaProducerFactory<String, String> factory = new DefaultKafkaProducerFactory<>(props);
        // Outbox의 pending 수는 "아직 보내지 못한 양"만 알려준다. 발행 자체가 느려지는지
        // 실패하는지는 producer client 지표로만 보인다.
        factory.addListener(new MicrometerProducerListener<>(meterRegistry));
        return factory;
    }

    @Bean
    public KafkaTemplate<String, String> kafkaTemplate(ProducerFactory<String, String> producerFactory) {
        return new KafkaTemplate<>(producerFactory);
    }
}
