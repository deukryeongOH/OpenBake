package com.openbake.interaction.application;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.openbake.common.security.CurrentMemberProvider;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class ProductViewRecorderTest {

    @Mock
    private CurrentMemberProvider currentMemberProvider;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Test
    void anonymousViewIsNotPublished() {
        when(currentMemberProvider.findId()).thenReturn(Optional.empty());

        new ProductViewRecorder(currentMemberProvider, eventPublisher).record(2L, null);

        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void loggedInViewCarriesMemberProductAndDrop() {
        when(currentMemberProvider.findId()).thenReturn(Optional.of(1L));
        ArgumentCaptor<ProductViewedEvent> captor = ArgumentCaptor.forClass(ProductViewedEvent.class);

        new ProductViewRecorder(currentMemberProvider, eventPublisher).record(2L, 3L);

        verify(eventPublisher).publishEvent(captor.capture());
        org.assertj.core.api.Assertions.assertThat(captor.getValue())
                .extracting(ProductViewedEvent::memberId, ProductViewedEvent::productId, ProductViewedEvent::dropId)
                .containsExactly(1L, 2L, 3L);
    }
}
