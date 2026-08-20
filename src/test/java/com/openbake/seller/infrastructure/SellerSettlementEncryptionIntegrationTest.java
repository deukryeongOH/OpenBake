package com.openbake.seller.infrastructure;

import com.openbake.product.infrastructure.elasticsearch.ProductSearchRepository;
import com.openbake.seller.domain.Seller;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Seller의 정산 계좌 정보가 DB에는 암호문으로 저장되고,
 * 엔티티 조회 시에는 평문으로 복호화되는지 실제 영속성 컨텍스트/DB를 통해 검증한다.
 *
 * 검증 대상은 JPA 영속화와 암호화 컨버터뿐이지만 @SpringBootTest 가 전체 컨텍스트를 올리므로
 * Elasticsearch 빈까지 함께 생성된다. SimpleElasticsearchRepository 는 생성 시점에 실제로 접속을 시도해,
 * 로컬에 ES 가 떠 있지 않으면 이 테스트가 컨텍스트 로딩 단계에서 실패한다.
 * 이 테스트는 검색과 무관하므로 해당 빈들을 대체해 외부 의존성을 끊는다.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class SellerSettlementEncryptionIntegrationTest {

    @MockitoBean
    private ProductSearchRepository productSearchRepository;

    @MockitoBean
    private ElasticsearchOperations elasticsearchOperations;

    @Autowired
    private SellerJpaRepository sellerJpaRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    @DisplayName("저장된 계좌번호/예금주 컬럼 값은 평문과 다르고, 다시 조회하면 평문으로 복호화된다")
    void settlementAccountFields_areEncryptedAtRestAndDecryptedOnRead() {
        String plainAccountNumber = "110-234-567890";
        String plainAccountHolder = "홍길동";

        Seller seller = new Seller(1L, "동네빵집", "123-45-67890", "서울시 강남구",
                "홍길동", true, "004", plainAccountNumber, plainAccountHolder, true);
        Seller saved = sellerJpaRepository.saveAndFlush(seller);
        entityManager.clear();

        Object[] rawRow = (Object[]) entityManager
                .createNativeQuery("SELECT settlement_account_number, settlement_account_holder FROM sellers WHERE id = :id")
                .setParameter("id", saved.getId())
                .getSingleResult();

        assertThat((String) rawRow[0]).isNotEqualTo(plainAccountNumber);
        assertThat((String) rawRow[1]).isNotEqualTo(plainAccountHolder);

        Seller reloaded = sellerJpaRepository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getSettlementAccountNumber()).isEqualTo(plainAccountNumber);
        assertThat(reloaded.getSettlementAccountHolder()).isEqualTo(plainAccountHolder);
    }

    @Test
    @DisplayName("동일한 평문을 두 번 저장하면 암호문(IV)이 서로 다르다")
    void samePlainText_producesDifferentCipherTextEachSave() {
        Seller first = sellerJpaRepository.saveAndFlush(new Seller(1L, "동네빵집", "123-45-67890",
                "서울시 강남구", "홍길동", true, "004", "110-234-567890", "홍길동", true));
        Seller second = sellerJpaRepository.saveAndFlush(new Seller(2L, "옆동네빵집", "123-45-67891",
                "서울시 서초구", "홍길동", true, "004", "110-234-567890", "홍길동", true));
        entityManager.clear();

        String firstCipher = (String) entityManager
                .createNativeQuery("SELECT settlement_account_number FROM sellers WHERE id = :id")
                .setParameter("id", first.getId())
                .getSingleResult();
        String secondCipher = (String) entityManager
                .createNativeQuery("SELECT settlement_account_number FROM sellers WHERE id = :id")
                .setParameter("id", second.getId())
                .getSingleResult();

        assertThat(firstCipher).isNotEqualTo(secondCipher);
    }
}
