package com.openbake.drop.application;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.openbake.drop.application.dto.DropInfoResult;
import com.openbake.drop.application.service.DropDetailService;
import com.openbake.drop.application.service.DropService;
import com.openbake.interaction.application.ProductViewRecorder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DropDetailServiceTest {

    @Mock
    private DropService dropService;
    @Mock
    private ProductViewRecorder viewRecorder;

    @Test
    void recordsProductAndDropTogether() {
        DropDetailService service = new DropDetailService(dropService, viewRecorder);
        DropInfoResult result = org.mockito.Mockito.mock(DropInfoResult.class);
        when(result.productId()).thenReturn(9L);
        when(dropService.getDropInfo(4L)).thenReturn(result);

        service.get(4L);

        verify(viewRecorder).record(9L, 4L);
    }
}
