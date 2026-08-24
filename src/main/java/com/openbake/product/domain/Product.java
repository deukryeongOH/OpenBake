package com.openbake.product.domain;

import com.openbake.common.exception.BusinessException;
import com.openbake.common.exception.ErrorCode;
import com.openbake.product.application.dto.ProductInfoCommand;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.BatchSize;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

@Entity
@Getter
@Table(name = "products")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name; // 상품 이름

    @Column(columnDefinition = "TEXT", nullable = false)
    private String description; // 상품 설명

    @Column(columnDefinition = "TEXT", nullable = false)
    private String imageUrl; // 이미지 경로 (DB 백업 크기 줄일 수 있고 로드 분산 가능, BLOB으로 저장 시 파일과 DB 간 일관성 관리 쉽고 동기화 보장 but DB 용량 급증)

    @Column(nullable = false)
    private int price; // 상품 가격

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Type type; // 상품 타입 (드롭, 일반)

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Category category; // 상품 카테고리

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProductStatus status = ProductStatus.SELLING; // 상품 상태 (판매중/품절/삭제됨)

    @ElementCollection(fetch = FetchType.LAZY)
    @BatchSize(size = 20)
    @CollectionTable(
            name = "product_pickup_available_dates",
            joinColumns = @JoinColumn(name = "product_id")
    )
    @Column(name = "available_date", nullable = false)
    private Set<LocalDate> pickUpAvailableDates = new HashSet<>(); // 픽업 가능 날짜

    @Column(nullable = false)
    private Long sellerId; // 판매자 ID

    @Builder
    public Product(String name, String description, String imageUrl, int price, Long sellerId, Set<LocalDate> pickUpAvailableDates, Category category, Type type) {
        validateProductInfo(name, description, imageUrl, price, category);
        validatePickUpDates(pickUpAvailableDates);

        if (type == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "타입을 지정해주세요.");
        }

        this.name = name;
        this.description = description;
        this.imageUrl = imageUrl;
        this.price = price;
        this.category = category;
        this.sellerId = sellerId;
        this.type = type;

        this.pickUpAvailableDates.addAll(pickUpAvailableDates);
    }

    public void updateProduct(ProductInfoCommand command) {
        validateProductInfo(command.name(), command.description(), command.imageUrl(), command.price(), command.category());
        validatePickUpDates(command.pickupDates());

        this.name = command.name();
        this.description = command.description();
        this.imageUrl = command.imageUrl();
        this.price = command.price();
        this.pickUpAvailableDates.clear();
        this.pickUpAvailableDates.addAll(command.pickupDates());
        this.category = command.category();
    }

    private void validateProductInfo(String name, String description, String imageUrl, int price, Category category) {
        if (name == null || name.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "이름을 입력해주세요.");
        }
        if (description == null || description.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "상품 세부사항 및 설명을 입력해주세요.");
        }
        if (imageUrl == null || imageUrl.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "상품 관련 이미지를 첨부해주세요.");
        }
        if (price <= 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "가격은 0보다 커야 합니다.");
        }
        if (category == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "카테고리를 지정해주세요.");
        }
    }

    private void validatePickUpDates(Set<LocalDate> pickUpAvailableDates) {
        if (pickUpAvailableDates == null || pickUpAvailableDates.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_PICKUP_DATE, "픽업 가능 날짜는 최소 하루 이상 필요합니다.");
        }

        LocalDate today = LocalDate.now();
        boolean hasPastDate = pickUpAvailableDates.stream()
                .anyMatch(date -> date.isBefore(today));

        if (hasPastDate) {
            throw new BusinessException(ErrorCode.INVALID_PICKUP_DATE, "픽업 가능 날짜는 오늘 이후여야 합니다.");
        }
    }

    public void updateImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public void markSoldOut() {
        this.status = ProductStatus.SOLD_OUT;
    }

    public void markSelling() {
        this.status = ProductStatus.SELLING;
    }

    public void markDeleted() {
        this.status = ProductStatus.DELETED;
    }
}
