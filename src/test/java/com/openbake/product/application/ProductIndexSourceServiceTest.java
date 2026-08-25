package com.openbake.product.application;

import com.openbake.product.domain.Product;
import com.openbake.product.domain.ProductRepository;
import com.openbake.product.presentation.dto.ProductIndexSourceResponse;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@code GET /internal/v1/products/ids}는 ai-service 백필·정합성 대조가
 * 임베딩 대상을 열거하는 소스다.
 *
 * <p>삭제된 상품이 섞이면 reconcile이 "core에는 있는데 벡터가 없다"고 판단해
 * <b>삭제한 상품의 벡터를 다시 만든다.</b> 삭제 대상 판정에서도 빠져 정리되지 않는다.
 * 그래서 전체 조회가 아니라 삭제 제외 조회를 써야 한다.
 */
class ProductIndexSourceServiceTest {

    @Test
    void indexSourcesExcludeDeletedProducts() {
        ProductRepository productRepository = mock(ProductRepository.class);
        Page<Product> page = new PageImpl<>(List.of());
        when(productRepository.findAllIndexTargets(any(Pageable.class))).thenReturn(page);

        new ProductIndexSourceService(productRepository).findPage(0, 100);

        verify(productRepository).findAllIndexTargets(PageRequest.of(
                0, 100, org.springframework.data.domain.Sort.by(
                        org.springframework.data.domain.Sort.Direction.ASC, "id")));
        // 삭제 상품까지 돌려주는 전체 조회로 되돌아가면 안 된다.
        verify(productRepository, never()).findAllByType(any());
    }
}
