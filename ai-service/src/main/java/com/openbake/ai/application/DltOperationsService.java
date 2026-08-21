package com.openbake.ai.application;

import com.openbake.ai.domain.ConsumedEventRepository;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
public class DltOperationsService {

    private static final Duration POLL_TIMEOUT = Duration.ofSeconds(2);

    private final ConsumerFactory<String, String> consumerFactory;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ConsumedEventRepository consumedEventRepository;
    private final ObjectMapper objectMapper;
    private final RecoveryProperties properties;

    public DltOperationsService(
            @Qualifier("dltConsumerFactory") ConsumerFactory<String, String> consumerFactory,
            KafkaTemplate<String, String> kafkaTemplate,
            ConsumedEventRepository consumedEventRepository,
            ObjectMapper objectMapper,
            RecoveryProperties properties) {
        this.consumerFactory = consumerFactory;
        this.kafkaTemplate = kafkaTemplate;
        this.consumedEventRepository = consumedEventRepository;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    public List<DltRecord> fetch(String topic) {
        requireAllowed(topic);
        List<ConsumerRecord<String, String>> records = new ArrayList<>();
        try (Consumer<String, String> consumer = consumerFactory.createConsumer()) {
            List<TopicPartition> partitions = consumer.partitionsFor(topic).stream()
                    .map(info -> new TopicPartition(topic, info.partition()))
                    .toList();
            consumer.assign(partitions);
            var beginnings = consumer.beginningOffsets(partitions);
            var ends = consumer.endOffsets(partitions);
            for (TopicPartition partition : partitions) {
                long start = Math.max(
                        beginnings.get(partition),
                        ends.get(partition) - properties.dlt().maxFetch());
                consumer.seek(partition, start);
            }
            int emptyPolls = 0;
            while (emptyPolls < 2) {
                var polled = consumer.poll(POLL_TIMEOUT);
                polled.forEach(records::add);
                emptyPolls = polled.isEmpty() ? emptyPolls + 1 : 0;
                boolean reachedEnds = partitions.stream()
                        .allMatch(partition -> consumer.position(partition) >= ends.get(partition));
                if (reachedEnds) {
                    break;
                }
            }
        }
        return records.stream()
                .sorted(Comparator.comparingLong(ConsumerRecord<String, String>::timestamp).reversed())
                .limit(properties.dlt().maxFetch())
                .map(this::describe)
                .toList();
    }

    public RepublishResult republish(List<DltSelection> selections) {
        List<DltSelection> published = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        for (DltSelection selection : selections == null ? List.<DltSelection>of() : selections) {
            requireAllowed(selection.dltTopic());
            ConsumerRecord<String, String> record = findExact(selection);
            String originalTopic = originalTopic(record);
            ParsedPayload parsed = parsePayload(record.value());
            if (parsed.eventId() == null) {
                warnings.add(selector(selection) + ": eventId를 검증할 수 없음");
            } else if (consumedEventRepository.existsById(parsed.eventId())) {
                warnings.add(selector(selection) + ": eventId=" + parsed.eventId() + "는 이미 소비됨");
            }
            RecordHeaders headers = new RecordHeaders();
            record.headers().forEach(header -> {
                if (!header.key().startsWith("kafka_dlt-")) {
                    headers.add(header);
                }
            });
            ProducerRecord<String, String> outbound = new ProducerRecord<>(
                    originalTopic, null, record.timestamp(), record.key(), record.value(), headers);
            kafkaTemplate.send(outbound).join();
            published.add(selection);
        }
        return new RepublishResult(List.copyOf(published), List.copyOf(warnings));
    }

    private ConsumerRecord<String, String> findExact(DltSelection selection) {
        TopicPartition partition = new TopicPartition(selection.dltTopic(), selection.partition());
        try (Consumer<String, String> consumer = consumerFactory.createConsumer()) {
            consumer.assign(List.of(partition));
            consumer.seek(partition, selection.offset());
            var records = consumer.poll(POLL_TIMEOUT).records(partition);
            return records.stream()
                    .filter(record -> record.offset() == selection.offset())
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException(
                            "DLT record not found: " + selector(selection)));
        }
    }

    private DltRecord describe(ConsumerRecord<String, String> record) {
        ParsedPayload payload = parsePayload(record.value());
        return new DltRecord(
                record.topic(), record.partition(), record.offset(), originalTopic(record), record.key(),
                payload.eventId() == null ? null : payload.eventId().toString(),
                headerText(record, KafkaHeaders.DLT_EXCEPTION_FQCN),
                headerText(record, KafkaHeaders.DLT_EXCEPTION_MESSAGE),
                payload.eventType(), payload.domainSummary());
    }

    private ParsedPayload parsePayload(String value) {
        try {
            JsonNode root = objectMapper.readTree(value);
            UUID eventId = uuid(text(root, "eventId"));
            String eventType = firstText(root, "eventType", "interactionType");
            List<String> ids = new ArrayList<>();
            for (String field : List.of("productId", "memberId", "dropId")) {
                String fieldValue = text(root, field);
                if (fieldValue != null) {
                    ids.add(field + "=" + fieldValue);
                }
            }
            return new ParsedPayload(eventId, eventType, String.join(",", ids));
        } catch (Exception ignored) {
            return new ParsedPayload(null, null, "unparseable");
        }
    }

    private String originalTopic(ConsumerRecord<String, String> record) {
        String header = headerText(record, KafkaHeaders.DLT_ORIGINAL_TOPIC);
        return header == null ? record.topic().substring(0, record.topic().length() - 4) : header;
    }

    private String headerText(ConsumerRecord<String, String> record, String name) {
        Header header = record.headers().lastHeader(name);
        return header == null || header.value() == null
                ? null : new String(header.value(), StandardCharsets.UTF_8);
    }

    private String firstText(JsonNode root, String... fields) {
        for (String field : fields) {
            String value = text(root, field);
            if (value != null) return value;
        }
        return null;
    }

    private String text(JsonNode root, String field) {
        JsonNode value = root.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private UUID uuid(String value) {
        try {
            return value == null ? null : UUID.fromString(value);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private void requireAllowed(String topic) {
        Set<String> allowed = properties.dlt().allowedTopics();
        if (topic == null || !allowed.contains(topic)) {
            throw new IllegalArgumentException("unsupported DLT topic");
        }
    }

    private String selector(DltSelection selection) {
        return selection.dltTopic() + "-" + selection.partition() + "@" + selection.offset();
    }

    private record ParsedPayload(UUID eventId, String eventType, String domainSummary) {
    }

    public record DltSelection(String dltTopic, int partition, long offset) {
    }

    public record DltRecord(
            String dltTopic, int partition, long offset, String originalTopic, String key,
            String eventId, String errorClass, String errorMessage,
            String eventType, String domainSummary) {
    }

    public record RepublishResult(List<DltSelection> published, List<String> warnings) {
    }
}
