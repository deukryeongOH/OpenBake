package com.openbake.product.infrastructure.elasticsearch;

import com.openbake.product.application.port.ProductSearchPort;
import com.openbake.product.domain.Category;
import com.openbake.product.domain.Product;
import com.openbake.product.domain.ProductStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.stereotype.Repository;

import java.util.List;

import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;

@Repository
@RequiredArgsConstructor
public class ProductSearchAdapter implements ProductSearchPort {

    private final ElasticsearchOperations elasticsearchOperations;
    private final ProductSearchRepository productSearchRepository;

    @Override
    public List<Long> searchIds(String keyword, Category category, Pageable pageable) {
        NativeQuery query = buildSearchQuery(keyword, category, pageable);
        SearchHits<ProductDocument> searchHits = elasticsearchOperations.search(query, ProductDocument.class);

        return searchHits.getSearchHits().stream()
                .map(SearchHit::getContent)
                .map(ProductDocument::getId)
                .toList();
    }

    @Override
    public long countBySearch(String keyword, Category category) {
        NativeQuery query = buildSearchQuery(keyword, category, Pageable.unpaged());
        SearchHits<ProductDocument> searchHits = elasticsearchOperations.search(query, ProductDocument.class);
        return searchHits.getTotalHits();
    }

    @Override
    public void index(Product product) {
        productSearchRepository.save(ProductDocument.from(product));
    }

    @Override
    public void deleteIndex(Long productId) {
        productSearchRepository.deleteById(productId);
    }

    @Override
    public void indexAll(List<Product> products) {
        List<ProductDocument> documents = products.stream()
                .map(ProductDocument::from)
                .toList();
        productSearchRepository.saveAll(documents);
    }

    @Override
    public List<Long> findAllIndexedIds() {
        NativeQuery query = NativeQuery.builder()
                .withQuery(Query.of(q -> q.matchAll(m -> m)))
                .withFields("_id")
                .withPageable(Pageable.ofSize(10000))
                .build();

        SearchHits<ProductDocument> searchHits = elasticsearchOperations.search(query, ProductDocument.class);
        return searchHits.getSearchHits().stream()
                .map(SearchHit::getContent)
                .map(ProductDocument::getId)
                .toList();
    }

    @Override
    public List<String> autocomplete(String prefix, int size) {
        NativeQuery query = NativeQuery.builder()
                .withQuery(Query.of(q -> q
                        .bool(b -> b
                                .must(m -> m
                                        .match(mt -> mt
                                                .field("name.autocomplete")
                                                .query(prefix)
                                        )
                                )
                                .filter(f -> f
                                        .term(t -> t
                                                .field("status")
                                                .value(ProductStatus.SELLING.name())
                                        )
                                )
                        )
                ))
                .withPageable(Pageable.ofSize(size))
                .build();

        SearchHits<ProductDocument> searchHits = elasticsearchOperations.search(query, ProductDocument.class);

        return searchHits.getSearchHits().stream()
                .map(SearchHit::getContent)
                .map(ProductDocument::getName)
                .distinct()
                .toList();
    }

    private NativeQuery buildSearchQuery(String keyword, Category category, Pageable pageable) {
        BoolQuery.Builder boolBuilder = new BoolQuery.Builder();

        // 판매중 상품만 필터링
        boolBuilder.filter(Query.of(q -> q
                .term(t -> t
                        .field("status")
                        .value(ProductStatus.SELLING.name())
                )
        ));

        if (keyword != null && !keyword.isBlank()) {
            boolBuilder.must(Query.of(q -> q
                    .multiMatch(m -> m
                            .query(keyword)
                            .fields("name^3", "description")
                            .fuzziness("AUTO")
                    )
            ));
        }

        if (category != null) {
            boolBuilder.filter(Query.of(q -> q
                    .term(t -> t
                            .field("category")
                            .value(category.name())
                    )
            ));
        }

        if (keyword == null && category == null) {
            boolBuilder.must(Query.of(q -> q.matchAll(m -> m)));
        }

        NativeQuery query = NativeQuery.builder()
                .withQuery(Query.of(q -> q.bool(boolBuilder.build())))
                .build();

        if (pageable.isPaged()) {
            query.setPageable(pageable);
        }

        return query;
    }
}
