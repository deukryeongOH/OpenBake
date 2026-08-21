package com.openbake.member.application;

import com.openbake.common.event.EventTopics;
import com.openbake.common.event.MemberWithdrawnEvent;
import com.openbake.member.outbox.OutboxEvent;
import com.openbake.member.outbox.OutboxEventRepository;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
@RequiredArgsConstructor
public class MemberWithdrawnOutboxWriter {

    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    public void write(Long memberId, Instant withdrawnAt) {
        MemberWithdrawnEvent event = MemberWithdrawnEvent.create(memberId, withdrawnAt);
        event.validate();
        String payload = objectMapper.writeValueAsString(event);
        outboxEventRepository.save(OutboxEvent.create(
                event.eventId().toString(), EventTopics.MEMBER_WITHDRAWN,
                memberId.toString(), "WITHDRAWN", event.eventVersion(), payload, event.occurredAt()));
    }
}
