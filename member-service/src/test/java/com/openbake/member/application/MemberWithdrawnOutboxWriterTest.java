package com.openbake.member.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import com.openbake.common.event.MemberWithdrawnEvent;
import com.openbake.member.outbox.OutboxEvent;
import com.openbake.member.outbox.OutboxEventRepository;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class MemberWithdrawnOutboxWriterTest {

    @Mock
    private OutboxEventRepository repository;

    @Test
    void usesSameEventIdInPayloadAndOutbox() {
        ObjectMapper mapper = JsonMapper.builder().findAndAddModules().build();
        MemberWithdrawnOutboxWriter writer = new MemberWithdrawnOutboxWriter(repository, mapper);
        Instant withdrawnAt = Instant.parse("2026-08-20T01:00:00Z");

        writer.write(7L, withdrawnAt);

        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(repository).save(captor.capture());
        OutboxEvent outbox = captor.getValue();
        MemberWithdrawnEvent payload = mapper.readValue(outbox.getPayload(), MemberWithdrawnEvent.class);
        assertThat(outbox.getEventId()).isEqualTo(payload.eventId().toString());
        assertThat(outbox.getEventKey()).isEqualTo("7");
        assertThat(outbox.getTopic()).isEqualTo("member.withdrawn.v1");
        assertThat(payload.withdrawnAt()).isEqualTo(withdrawnAt);
    }
}
