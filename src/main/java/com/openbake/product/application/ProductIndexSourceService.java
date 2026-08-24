package com.openbake.product.application;

import com.openbake.product.domain.ProductRepository;
import com.openbake.product.presentation.dto.ProductIndexSourceResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProductIndexSourceService {

    private final ProductRepository productRepository;

    @Transactional(readOnly = true)
    public Page<ProductIndexSourceResponse> findPage(int page, int size) {
        PageRequest request = PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "id"));
        return productRepository.findAllIndexTargets(request).map(ProductIndexSourceResponse::from);
    }
}
