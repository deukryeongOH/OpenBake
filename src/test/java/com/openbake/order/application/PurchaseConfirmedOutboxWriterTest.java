package com.openbake.order.application;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.openbake.interaction.application.InteractionOutboxWriter;
import com.openbake.order.application.port.DropPort;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PurchaseConfirmedOutboxWriterTest {

    @Mock
    private DropPort dropPort;
    @Mock
    private InteractionOutboxWriter interactionOutboxWriter;

    @Test
    void convertsDropToProductAndWritesPurchase() {
        PurchaseConfirmedOutboxWriter writer =
                new PurchaseConfirmedOutboxWriter(dropPort, interactionOutboxWriter);
        Instant occurredAt = Instant.parse("2026-08-20T01:00:00Z");
        when(dropPort.getProductId(3L)).thenReturn(9L);

        writer.write(1L, 3L, 2, 7L, occurredAt);

        verify(interactionOutboxWriter)
                .purchaseConfirmed(1L, 9L, 3L, 2, 7L, occurredAt);
    }

    @Test
    void isolatesDropLookupFailure() {
        PurchaseConfirmedOutboxWriter writer =
                new PurchaseConfirmedOutboxWriter(dropPort, interactionOutboxWriter);
        when(dropPort.getProductId(3L)).thenThrow(new IllegalStateException("missing"));

        writer.write(1L, 3L, 2, 7L, Instant.now());

        verifyNoInteractions(interactionOutboxWriter);
    }
}
