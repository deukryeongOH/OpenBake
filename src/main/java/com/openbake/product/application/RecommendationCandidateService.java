package com.openbake.product.application;

import com.openbake.product.domain.ProductInventory;
import com.openbake.product.domain.ProductStatus;
import com.openbake.product.domain.Type;
import com.openbake.product.infrastructure.ProductInventoryJpaRepository;
import com.openbake.product.infrastructure.ProductJpaRepository;
import com.openbake.seller.domain.SellerRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RecommendationCandidateService {

    private final ProductJpaRepository productRepository;
    private final ProductInventoryJpaRepository inventoryRepository;
    private final SellerRepository sellerRepository;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<RecommendationProduct> validate(Long memberId, List<Long> productIds) {
        LinkedHashSet<Long> uniqueIds = new LinkedHashSet<>(productIds);
        Long sellerId = findSellerId(memberId);
        LocalDate today = LocalDate.now(clock);

        Map<Long, ProductInventory> inventories = inventoryRepository.findAllById(uniqueIds)
                .stream()
                .collect(Collectors.toMap(ProductInventory::getProductId, Function.identity()));

        return productRepository.findRecommendationCandidates(uniqueIds, today).stream()
                .filter(product -> product.getType() == Type.GENERAL)
                .filter(product -> product.getStatus() == ProductStatus.SELLING)
                .filter(product -> product.getPickUpAvailableDates().stream()
                        .anyMatch(date -> !date.isBefore(today)))
                .filter(product -> sellerId == null || !sellerId.equals(product.getSellerId()))
                .map(product -> {
                    ProductInventory inventory = inventories.get(product.getId());
                    if (inventory == null || inventory.getRemainQuantity() < 1) {
                        return null;
                    }
                    return new RecommendationProduct(
                            product.getId(),
                            product.getName(),
                            product.getImageUrl(),
                            product.getPrice(),
                            product.getCategory(),
                            inventory.getRemainQuantity());
                })
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<RecommendationProduct> latest(Long memberId, int size) {
        return productRepository.findLatestRecommendationCandidates(
                LocalDate.now(clock),
                findSellerId(memberId),
                PageRequest.of(0, size));
    }

    private Long findSellerId(Long memberId) {
        return sellerRepository.findByMemberId(memberId)
                .map(seller -> seller.getId())
                .orElse(null);
    }
}
