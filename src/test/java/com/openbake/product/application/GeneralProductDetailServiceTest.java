package com.openbake.product.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.openbake.common.exception.BusinessException;
import com.openbake.interaction.application.ProductViewRecorder;
import com.openbake.product.application.dto.ProductInfoResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GeneralProductDetailServiceTest {

    @Mock
    private ProductService productService;
    @Mock
    private ProductViewRecorder viewRecorder;

    @Test
    void recordsOnlyAfterSuccessfulDetailLookup() {
        GeneralProductDetailService service =
                new GeneralProductDetailService(productService, viewRecorder);
        ProductInfoResult result = org.mockito.Mockito.mock(ProductInfoResult.class);
        when(result.productId()).thenReturn(8L);
        when(productService.getGeneralProductInfo(8L)).thenReturn(result);

        service.get(8L);

        verify(viewRecorder).record(8L, null);
    }

    @Test
    void doesNotRecordFailedLookup() {
        GeneralProductDetailService service =
                new GeneralProductDetailService(productService, viewRecorder);
        when(productService.getGeneralProductInfo(8L))
                .thenThrow(BusinessException.class);

        assertThatThrownBy(() -> service.get(8L)).isInstanceOf(BusinessException.class);
        verifyNoInteractions(viewRecorder);
    }
}
