package com.openbake.product.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.openbake.product.domain.Category;
import com.openbake.product.domain.Product;
import com.openbake.product.domain.Type;
import com.openbake.product.infrastructure.elasticsearch.ProductSearchRepository;
import java.time.LocalDate;
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
 * {@code findSellerIdById}가 <b>정말 판매자 ID를 돌려주는지</b> 확인한다.
 *
 * <p><b>왜 이 시험이 필요한가.</b> 이 메서드는 원래 질의 없이 이름만 선언되어 있었다.
 * 그런데 Spring Data JPA는 {@code find}와 {@code By} 사이의 글자를 설명으로 보고
 * 버린다. 그래서 {@code findSellerIdById}는 {@code findById}와 똑같이 해석되어
 * <b>{@code Product}를 반환</b>했고, 선언은 {@code Long}이라 호출하는 쪽에서
 * {@code ClassCastException}이 났다.
 *
 * <p><b>컴파일은 통과한다.</b> 질의 구현체를 Spring이 실행 중에 만들기 때문이다.
 * 2026-08-28 운영에서 90분간 25회 터졌고, 지표(5xx)에서 로그로 내려가서야 찾았다.
 *
 * <p>그래서 이 시험은 반환값이 맞는지만 본다. 반환 <b>타입</b>이 어긋나면 단언 이전에
 * {@code ClassCastException}으로 먼저 실패하므로, 그것만으로 회귀를 막는다.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ProductSellerIdQueryTest {

    // 이 시험은 검색과 무관하지만 @SpringBootTest가 전체 컨텍스트를 올려 ES 빈까지 만든다.
    // 실제 접속을 시도하므로 로컬에 ES 없이도 통과하도록 대체한다.
    @MockitoBean
    private ProductSearchRepository productSearchRepository;

    @MockitoBean
    private ElasticsearchOperations elasticsearchOperations;

    @Autowired
    private ProductJpaRepository productRepository;

    @Test
    @DisplayName("상품 ID로 판매자 ID를 돌려준다 — Product가 아니라 Long이어야 한다")
    void returnsSellerIdNotProduct() {
        Long sellerId = 42L;
        Product product = productRepository.save(Product.builder()
                .name("product")
                .description("description")
                .imageUrl("https://example.test/product")
                .price(1_000)
                .sellerId(sellerId)
                .pickUpAvailableDates(Set.of(LocalDate.now().plusDays(1)))
                .category(Category.MEAL_BREADS)
                .type(Type.GENERAL)
                .build());

        Long found = productRepository.findSellerIdById(product.getId());

        assertThat(found).isEqualTo(sellerId);
    }

    @Test
    @DisplayName("없는 상품이면 null을 돌려준다 — 예외로 터지지 않는다")
    void returnsNullWhenProductMissing() {
        assertThat(productRepository.findSellerIdById(-1L)).isNull();
    }
}
