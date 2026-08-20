package com.openbake.product.application;

import com.openbake.interaction.application.ProductViewRecorder;
import com.openbake.product.application.dto.ProductInfoResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GeneralProductDetailService {

    private final ProductService productService;
    private final ProductViewRecorder viewRecorder;

    @Transactional(readOnly = true)
    public ProductInfoResult get(Long productId) {
        ProductInfoResult result = productService.getGeneralProductInfo(productId);
        viewRecorder.record(result.productId(), null);
        return result;
    }
}
