package com.openbake.ai.infrastructure.elasticsearch;

import com.openbake.ai.application.EmbeddingFailureException;
import com.openbake.ai.application.EmbeddingProperties;
import com.openbake.ai.application.port.ProductEmbeddingIndex;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.core.query.Query;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.IndexOperations;
import org.springframework.data.elasticsearch.core.document.Document;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ElasticsearchProductEmbeddingIndex implements ProductEmbeddingIndex {

    private static final String MAPPING_PATH = "elasticsearch/product-embeddings-v1-mapping.json";

    private final ElasticsearchOperations operations;
    private final EmbeddingProperties properties;

    @Override
    public boolean exists(Long productId) {
        try {
            ensureIndex();
            return operations.exists(productId.toString(), coordinates());
        } catch (EmbeddingFailureException e) {
            throw e;
        } catch (Exception e) {
            throw EmbeddingFailureException.transientFailure("ELASTICSEARCH_READ_ERROR", e);
        }
    }

    @Override
    public void upsert(ProductEmbeddingIndexDocument document) {
        try {
            ensureIndex();
            operations.save(ProductEmbeddingDocument.from(document), coordinates());
        } catch (EmbeddingFailureException e) {
            throw e;
        } catch (Exception e) {
            throw EmbeddingFailureException.transientFailure("ELASTICSEARCH_WRITE_ERROR", e);
        }
    }

    @Override
    public void touch(Long productId, Instant sourceOccurredAt) {
        try {
            ensureIndex();
            ProductEmbeddingDocument document = operations.get(
                    productId.toString(), ProductEmbeddingDocument.class, coordinates());
            if (document == null) {
                throw EmbeddingFailureException.transientFailure("ELASTICSEARCH_DOCUMENT_MISSING", null);
            }
            document.touch(sourceOccurredAt);
            operations.save(document, coordinates());
        } catch (EmbeddingFailureException e) {
            throw e;
        } catch (Exception e) {
            throw EmbeddingFailureException.transientFailure("ELASTICSEARCH_WRITE_ERROR", e);
        }
    }

    @Override
    public void delete(Long productId) {
        try {
            ensureIndex();
            operations.delete(productId.toString(), coordinates());
        } catch (EmbeddingFailureException e) {
            throw e;
        } catch (Exception e) {
            throw EmbeddingFailureException.transientFailure("ELASTICSEARCH_DELETE_ERROR", e);
        }
    }

    @Override
    public java.util.List<Long> findAllProductIds() {
        try {
            ensureIndex();
            ArrayList<Long> ids = new ArrayList<>();
            int page = 0;
            while (true) {
                Query query = Query.findAll();
                query.setPageable(PageRequest.of(page, 1_000));
                var hits = operations.search(query, ProductEmbeddingDocument.class, coordinates());
                hits.forEach(hit -> {
                    ProductEmbeddingDocument content = hit.getContent();
                    ids.add(content.getProductId() == null
                            ? Long.valueOf(hit.getId()) : content.getProductId());
                });
                if (hits.getSearchHits().size() < 1_000) {
                    break;
                }
                page++;
            }
            ids.sort(Comparator.naturalOrder());
            return List.copyOf(ids);
        } catch (EmbeddingFailureException e) {
            throw e;
        } catch (Exception e) {
            throw EmbeddingFailureException.transientFailure("ELASTICSEARCH_READ_ERROR", e);
        }
    }

    private synchronized void ensureIndex() {
        IndexOperations indexOperations = operations.indexOps(coordinates());
        if (indexOperations.exists()) {
            return;
        }
        try {
            String mappingJson = new ClassPathResource(MAPPING_PATH)
                    .getContentAsString(StandardCharsets.UTF_8)
                    .replace("\"dims\": 1536", "\"dims\": " + properties.dimensions());
            Document mapping = Document.parse(mappingJson);
            indexOperations.create(Map.of(), mapping);
        } catch (IOException e) {
            throw EmbeddingFailureException.permanentFailure("ELASTICSEARCH_MAPPING_UNREADABLE", e);
        } catch (Exception e) {
            if (!indexOperations.exists()) {
                throw EmbeddingFailureException.transientFailure("ELASTICSEARCH_INDEX_CREATE_ERROR", e);
            }
        }
    }

    private IndexCoordinates coordinates() {
        return IndexCoordinates.of(properties.indexName());
    }
}
