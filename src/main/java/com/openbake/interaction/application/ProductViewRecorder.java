package com.openbake.interaction.application;

import com.openbake.common.security.CurrentMemberProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
@RequiredArgsConstructor
public class ProductViewRecorder {

    private final CurrentMemberProvider currentMemberProvider;
    private final ApplicationEventPublisher eventPublisher;

    public void record(Long productId, Long dropId) {
        currentMemberProvider.findId()
                .ifPresent(memberId -> eventPublisher.publishEvent(
                        new ProductViewedEvent(memberId, productId, dropId, Instant.now())));
    }
}
