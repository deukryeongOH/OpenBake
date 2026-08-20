package com.openbake.ai.infrastructure.config;

import java.util.Map;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.boot.kafka.autoconfigure.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.util.backoff.BackOff;
import org.springframework.util.backoff.BackOffExecution;
import tools.jackson.core.JacksonException;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.listener.ConsumerRecordRecoverer;
import com.openbake.common.event.EventTopics;

@Configuration
@Slf4j
public class KafkaConsumerConfig {

    /**
     * application.yml의 spring.kafka.consumer.*(group-id, auto-offset-reset,
     * enable-auto-commit 등)를 그대로 반영하기 위해 KafkaProperties에서 빌드해서 쓴다.
     * key/value deserializer는 ErrorHandlingDeserializer로 감싸서, 역직렬화 실패가
     * 컨테이너를 죽이지 않고 에러 핸들러로 넘어가게 한다.
     */
    @Bean
    public ConsumerFactory<String, String> consumerFactory(KafkaProperties kafkaProperties) {
        Map<String, Object> props = kafkaProperties.buildConsumerProperties();
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
        props.put(ErrorHandlingDeserializer.KEY_DESERIALIZER_CLASS, StringDeserializer.class);
        props.put(ErrorHandlingDeserializer.VALUE_DESERIALIZER_CLASS, StringDeserializer.class);
        return new DefaultKafkaConsumerFactory<>(props);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory(
            ConsumerFactory<String, String> consumerFactory,
            DefaultErrorHandler kafkaErrorHandler) {
        ConcurrentKafkaListenerContainerFactory<String, String> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.setCommonErrorHandler(kafkaErrorHandler);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.RECORD);
        return factory;
    }

    /**
     * Boot가 자동으로 만들어주는 KafkaTemplate은 제네릭이 <Object, Object>라 <String, String>
     * 주입 지점과 타입이 안 맞는다. DLT recoverer가 쓸 <String, String> 템플릿을 직접 정의한다.
     */
    @Bean
    public ProducerFactory<String, String> producerFactory(KafkaProperties kafkaProperties) {
        Map<String, Object> props = kafkaProperties.buildProducerProperties();
        props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        return new DefaultKafkaProducerFactory<>(props);
    }

    @Bean
    public KafkaTemplate<String, String> kafkaTemplate(ProducerFactory<String, String> producerFactory) {
        return new KafkaTemplate<>(producerFactory);
    }

    /**
     * 재시도 3회(1s, 5s, 30s) 후 DLT로 보낸다. 역직렬화·검증 실패는 재시도 없이 바로 DLT.
     * DLT 토픽명은 이미 만들어둔 소문자 `.dlt` 접미사에 맞춰 커스텀 resolver로 지정한다.
     */
    @Bean
    public DefaultErrorHandler kafkaErrorHandler(
            KafkaTemplate<String, String> kafkaTemplate, MeterRegistry meterRegistry) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                kafkaTemplate,
                (record, ex) -> new TopicPartition(record.topic() + ".dlt", 0));

        ConsumerRecordRecoverer observingRecoverer = (record, exception) -> {
            recoverer.accept(record, exception);
            if (EventTopics.MEMBER_WITHDRAWN.equals(record.topic())) {
                meterRegistry.counter("openbake.ai.member-withdrawn.dlt").increment();
                log.error("회원 탈퇴 이벤트 DLT 이동 key={} partition={} offset={}",
                        record.key(), record.partition(), record.offset(), exception);
            }
        };

        DefaultErrorHandler errorHandler = new DefaultErrorHandler(
                observingRecoverer, new FixedIntervalsBackOff(1_000L, 5_000L, 30_000L));
        errorHandler.addNotRetryableExceptions(
                JacksonException.class,
                IllegalArgumentException.class);
        return errorHandler;
    }

    /** Spring 기본 BackOff(고정 간격/고정 배율)로는 표현할 수 없는 불균등 재시도 간격 전용 구현. */
    private static final class FixedIntervalsBackOff implements BackOff {

        private final long[] intervalsMs;

        private FixedIntervalsBackOff(long... intervalsMs) {
            this.intervalsMs = intervalsMs;
        }

        @Override
        public BackOffExecution start() {
            return new BackOffExecution() {
                private int attempt = 0;

                @Override
                public long nextBackOff() {
                    if (attempt >= intervalsMs.length) {
                        return BackOffExecution.STOP;
                    }
                    return intervalsMs[attempt++];
                }
            };
        }
    }
}
