package com.openbake.product.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.openbake.product.domain.Category;
import com.openbake.product.domain.Product;
import com.openbake.product.domain.ProductInventory;
import com.openbake.product.domain.Type;
import com.openbake.product.infrastructure.ProductInventoryJpaRepository;
import com.openbake.product.infrastructure.ProductJpaRepository;
import com.openbake.seller.domain.Seller;
import com.openbake.seller.infrastructure.SellerJpaRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import com.openbake.product.infrastructure.elasticsearch.ProductSearchRepository;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class RecommendationCandidatePersistenceIntegrationTest {

    // 이 테스트는 검색과 무관하지만 @SpringBootTest가 전체 컨텍스트를 올려 ES 빈까지 생성한다.
    // SimpleElasticsearchRepository는 생성 시점에 실제 접속을 시도하므로 로컬에 ES 없이도 통과하도록 대체한다.
    @MockitoBean
    private ProductSearchRepository productSearchRepository;

    @MockitoBean
    private ElasticsearchOperations elasticsearchOperations;

    @Autowired
    private RecommendationCandidateService service;
    @Autowired
    private ProductJpaRepository productRepository;
    @Autowired
    private ProductInventoryJpaRepository inventoryRepository;
    @Autowired
    private SellerJpaRepository sellerRepository;

    @BeforeEach
    void clean() {
        inventoryRepository.deleteAll();
        productRepository.deleteAll();
        sellerRepository.deleteAll();
    }

    @Test
    void latestQueryAppliesEveryAvailabilityFilterAndOrdersByIdDescending() {
        LocalDate today = LocalDate.now();
        Product first = saveProduct(Type.GENERAL, 10L, today.plusDays(1), 2);
        Product soldOut = saveProduct(Type.GENERAL, 10L, today.plusDays(1), 2);
        soldOut.markSoldOut();
        productRepository.save(soldOut);
        saveProduct(Type.DROP, 10L, today.plusDays(1), 2);
        saveProduct(Type.GENERAL, 10L, today.plusDays(1), 0);
        Product noFuturePickup = saveProduct(Type.GENERAL, 10L, today.plusDays(1), 2);
        noFuturePickup.getPickUpAvailableDates().clear();
        noFuturePickup.getPickUpAvailableDates().add(today.minusDays(1));
        productRepository.save(noFuturePickup);
        Product newest = saveProduct(Type.GENERAL, 10L, today.plusDays(2), 3);

        List<RecommendationProduct> result = service.latest(100L, 100);

        assertThat(result).extracting(RecommendationProduct::productId)
                .containsExactly(newest.getId(), first.getId());
    }

    @Test
    void latestQueryExcludesCurrentSellersOwnProduct() {
        Seller seller = sellerRepository.save(new Seller(
                200L, "bakery", "business", "address", "owner", true,
                "bank", "account", "holder", true));
        Product own = saveProduct(Type.GENERAL, seller.getId(), LocalDate.now().plusDays(1), 2);
        Product other = saveProduct(Type.GENERAL, seller.getId() + 1, LocalDate.now().plusDays(1), 2);

        List<RecommendationProduct> result = service.latest(200L, 100);

        assertThat(result).extracting(RecommendationProduct::productId)
                .containsExactly(other.getId())
                .doesNotContain(own.getId());
    }

    private Product saveProduct(Type type, Long sellerId, LocalDate pickupDate, int remainQuantity) {
        Product product = Product.builder()
                .name("product")
                .description("description")
                .imageUrl("https://example.test/product")
                .price(1_000)
                .sellerId(sellerId)
                .pickUpAvailableDates(Set.of(pickupDate))
                .category(Category.MEAL_BREADS)
                .type(type)
                .build();
        productRepository.save(product);
        inventoryRepository.save(ProductInventory.builder()
                .productId(product.getId())
                .remainQuantity(remainQuantity)
                .totalQuantity(Math.max(1, remainQuantity))
                .build());
        return product;
    }
}
