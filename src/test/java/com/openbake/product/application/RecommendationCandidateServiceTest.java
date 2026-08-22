package com.openbake.product.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.openbake.product.domain.Category;
import com.openbake.product.domain.Product;
import com.openbake.product.domain.ProductInventory;
import com.openbake.product.domain.Type;
import com.openbake.product.infrastructure.ProductInventoryJpaRepository;
import com.openbake.product.infrastructure.ProductJpaRepository;
import com.openbake.seller.domain.Seller;
import com.openbake.seller.domain.SellerRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class RecommendationCandidateServiceTest {

    // Product.validatePickUpDates()가 LocalDate.now()로 검증하므로 하드코딩된 과거 날짜를 쓰면
    // 시간이 지나 실제 오늘을 넘기는 순간 테스트가 깨진다. 항상 실행 시점 기준으로 맞춘다.
    private static final LocalDate TODAY = LocalDate.now();

    private final ProductJpaRepository productRepository = mock(ProductJpaRepository.class);
    private final ProductInventoryJpaRepository inventoryRepository =
            mock(ProductInventoryJpaRepository.class);
    private final SellerRepository sellerRepository = mock(SellerRepository.class);
    private final RecommendationCandidateService service = new RecommendationCandidateService(
            productRepository,
            inventoryRepository,
            sellerRepository,
            Clock.fixed(TODAY.atStartOfDay(ZoneOffset.UTC).toInstant(), ZoneOffset.UTC));

    @BeforeEach
    void noSellerByDefault() {
        when(sellerRepository.findByMemberId(100L)).thenReturn(Optional.empty());
    }

    @Test
    void filtersSoldOutDropNoPickupNoStockDeletedAndOwnProducts() {
        Seller seller = seller(20L, 100L);
        when(sellerRepository.findByMemberId(100L)).thenReturn(Optional.of(seller));

        Product valid = product(1L, 10L, Type.GENERAL, TODAY.plusDays(1));
        Product soldOut = product(2L, 10L, Type.GENERAL, TODAY.plusDays(1));
        soldOut.markSoldOut();
        Product drop = product(3L, 10L, Type.DROP, TODAY.plusDays(1));
        Product noFuturePickup = product(4L, 10L, Type.GENERAL, TODAY.plusDays(1));
        noFuturePickup.getPickUpAvailableDates().clear();
        noFuturePickup.getPickUpAvailableDates().add(TODAY.minusDays(1));
        Product noStock = product(5L, 10L, Type.GENERAL, TODAY.plusDays(1));
        Product own = product(6L, 20L, Type.GENERAL, TODAY.plusDays(1));

        when(productRepository.findRecommendationCandidates(any(), any()))
                .thenReturn(List.of(valid, soldOut, drop, noFuturePickup, noStock, own));
        when(inventoryRepository.findAllById(any())).thenReturn(List.of(
                inventory(1L, 2), inventory(2L, 2), inventory(3L, 2),
                inventory(4L, 2), inventory(5L, 0), inventory(6L, 2)));

        List<RecommendationProduct> result = service.validate(
                100L, List.of(1L, 2L, 3L, 4L, 5L, 6L, 999L));

        assertEquals(List.of(1L), result.stream().map(RecommendationProduct::productId).toList());
    }

    @Test
    void buyerWithoutSellerDoesNotApplyOwnProductFilterAndDeduplicatesIds() {
        Product product = product(1L, 100L, Type.GENERAL, TODAY.plusDays(1));
        when(productRepository.findRecommendationCandidates(any(), any())).thenReturn(List.of(product));
        when(inventoryRepository.findAllById(any())).thenReturn(List.of(inventory(1L, 1)));

        List<RecommendationProduct> result = service.validate(100L, List.of(1L, 1L, 1L));

        assertEquals(List.of(1L), result.stream().map(RecommendationProduct::productId).toList());
        verify(productRepository).findRecommendationCandidates(Set.of(1L), TODAY);
        verify(inventoryRepository).findAllById(Set.of(1L));
        verify(sellerRepository, times(1)).findByMemberId(100L);
    }

    private Product product(Long id, Long sellerId, Type type, LocalDate pickupDate) {
        Product product = Product.builder()
                .name("product-" + id)
                .description("description")
                .imageUrl("https://example.test/" + id)
                .price(1_000)
                .sellerId(sellerId)
                .pickUpAvailableDates(Set.of(pickupDate))
                .category(Category.CAKES_TARTS)
                .type(type)
                .build();
        ReflectionTestUtils.setField(product, "id", id);
        return product;
    }

    private ProductInventory inventory(Long productId, int remainQuantity) {
        return ProductInventory.builder()
                .productId(productId)
                .remainQuantity(remainQuantity)
                .totalQuantity(Math.max(1, remainQuantity))
                .build();
    }

    private Seller seller(Long sellerId, Long memberId) {
        Seller seller = new Seller(
                memberId, "bakery", "business", "address", "owner", true,
                "bank", "account", "holder", true);
        ReflectionTestUtils.setField(seller, "id", sellerId);
        return seller;
    }
}
