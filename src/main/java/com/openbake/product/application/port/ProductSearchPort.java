package com.openbake.product.application.port;

import com.openbake.product.domain.Category;
import com.openbake.product.domain.Product;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface ProductSearchPort {

    List<Long> searchIds(String keyword, Category category, Pageable pageable);

    long countBySearch(String keyword, Category category);

    void index(Product product);

    void deleteIndex(Long productId);

    List<String> autocomplete(String prefix, int size);
}
