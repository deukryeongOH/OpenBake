package com.openbake.product.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.openbake.product.domain.Category;
import com.openbake.product.domain.Product;
import com.openbake.product.domain.Type;
import com.openbake.product.infrastructure.elasticsearch.ProductSearchRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@code findDropProductListBySellerId}가 <b>드롭 상품만</b> 돌려주는지 확인한다.
 *
 * <p><b>왜 이 시험이 필요한가.</b> 예전에는 이 자리에 조건 없는 {@code findAllBySellerId}가
 * 있었고, 판매자의 상품을 전부 가져온 뒤 {@code DropService.getMyDrops}가
 * {@code isGeneralProduct}로 하나씩 물어보며 일반 상품을 건너뛰었다. 그런데 그 질의는
 * 내부적으로 {@code ProductService.getProduct}를 타고, 이 메서드는 상태가
 * {@code DELETED}면 {@code PRODUCT_NOT_FOUND}를 던진다. 소프트 삭제는 행을 남기므로
 * 삭제된 일반 상품이 목록에 그대로 섞여 들어왔고, 건너뛸지 판단하려고 물어보는 순간
 * 예외가 올라가 <b>드롭 목록 조회 전체가 404</b>가 됐다. 판매자가 드롭과 무관한 일반
 * 상품을 하나 지우면 그 뒤로 자기 드롭 목록을 영영 볼 수 없었다.
 *
 * <p>지금은 질의가 {@code type = DROP}으로 걸러 오므로 서비스는 받은 것을 그대로 매핑만
 * 한다. 즉 <b>회귀를 막는 것은 이 질의 하나</b>다. 서비스 단위 시험은 포트를 목으로
 * 대체하므로 질의가 조건을 잃어버려도 잡아내지 못한다.
 *
 * <p>드롭 상품은 하드 삭제라 {@code status} 조건은 두지 않았다. 소프트 삭제는 ES 색인
 * 정합성 때문에 일반 상품에만 적용된다.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ProductDropListQueryTest {

    // 이 시험은 검색과 무관하지만 @SpringBootTest가 전체 컨텍스트를 올려 ES 빈까지 만든다.
    // 실제 접속을 시도하므로 로컬에 ES 없이도 통과하도록 대체한다.
    @MockitoBean
    private ProductSearchRepository productSearchRepository;

    @MockitoBean
    private ElasticsearchOperations elasticsearchOperations;

    @Autowired
    private ProductJpaRepository productRepository;

    private Product save(Long sellerId, String name, Type type) {
        return productRepository.save(Product.builder()
                .name(name)
                .description("description")
                .imageUrl("https://example.test/" + name)
                .price(1_000)
                .sellerId(sellerId)
                .pickUpAvailableDates(Set.of(LocalDate.now().plusDays(1)))
                .category(Category.MEAL_BREADS)
                .type(type)
                .build());
    }

    @Test
    @DisplayName("판매자의 드롭 상품만 돌려준다 — 일반 상품은 섞이지 않는다")
    void returnsOnlyDropProducts() {
        Long sellerId = 100L;
        Product dropProduct = save(sellerId, "drop-product", Type.DROP);
        save(sellerId, "general-product", Type.GENERAL);

        List<Product> found = productRepository.findDropProductListBySellerId(sellerId);

        assertThat(found).extracting(Product::getId).containsExactly(dropProduct.getId());
    }

    @Test
    @DisplayName("소프트 삭제된 일반 상품이 있어도 드롭 상품만 돌려준다 — 404 회귀 방지")
    void deletedGeneralProductDoesNotLeakIn() {
        Long sellerId = 101L;
        Product dropProduct = save(sellerId, "drop-product", Type.DROP);

        Product deletedGeneral = save(sellerId, "deleted-general", Type.GENERAL);
        deletedGeneral.markDeleted();   // 소프트 삭제 — 행은 그대로 남는다
        productRepository.save(deletedGeneral);

        List<Product> found = productRepository.findDropProductListBySellerId(sellerId);

        // 삭제된 일반 상품이 하나라도 새어 들어오면 호출부가 그 상품을 조회하다 404로 떨어진다
        assertThat(found).extracting(Product::getId).containsExactly(dropProduct.getId());
    }

    @Test
    @DisplayName("다른 판매자의 드롭 상품은 돌려주지 않는다")
    void excludesOtherSellersDropProducts() {
        Long sellerId = 102L;
        Long otherSellerId = 103L;
        Product mine = save(sellerId, "my-drop", Type.DROP);
        save(otherSellerId, "other-drop", Type.DROP);

        List<Product> found = productRepository.findDropProductListBySellerId(sellerId);

        assertThat(found).extracting(Product::getId).containsExactly(mine.getId());
    }

    @Test
    @DisplayName("드롭 상품이 없으면 빈 목록을 돌려준다 — 예외로 터지지 않는다")
    void returnsEmptyWhenNoDropProduct() {
        Long sellerId = 104L;
        save(sellerId, "general-only", Type.GENERAL);

        assertThat(productRepository.findDropProductListBySellerId(sellerId)).isEmpty();
    }
}