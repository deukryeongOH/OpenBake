package com.openbake.product.domain;

import com.openbake.common.exception.BusinessException;
import com.openbake.common.exception.ErrorCode;
import com.openbake.product.application.dto.GeneralProductInfoCommand;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

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

    @Column(nullable = false)
    private int totalQuantity; // 총 발매 수량 (한정 수량)

    @Column(nullable = false)
    private int remainQuantity; // 남은 재고 수량

    @Column(nullable = false)
    private Category category; // 상품 카테고리

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(
            name = "pickup_available_dates",
            joinColumns = @JoinColumn(name = "product_id")
    )
    @Column(name = "available_date", nullable = false)
    private Set<LocalDate> pickUpAvailableDate = new HashSet<>(); // 픽업 가능 날짜

    @Column(nullable = false)
    private Long sellerId; // 판매자 ID

    @Builder
    public Product(String name, String description, String imageUrl, int price, int totalQuantity, int remainQuantity, Long sellerId, Set<LocalDate> pickUpAvailableDate, Category category) {
        validateProductInfo(name, description, imageUrl, price, totalQuantity, category);
        validatePickUpDates(pickUpAvailableDate);

        this.name = name;
        this.description = description;
        this.imageUrl = imageUrl;
        this.price = price;
        this.category = category;
        this.totalQuantity = totalQuantity;
        this.remainQuantity = totalQuantity;
        this.sellerId = sellerId;

        this.pickUpAvailableDate.addAll(pickUpAvailableDate);
    }

    public void updateProduct(GeneralProductInfoCommand command) {
        validateProductInfo(command.name(), command.description(), command.imageUrl(), command.price(), command.totalQuantity(), command.category());
        validatePickUpDates(command.pickupDates());

        this.name = command.name();
        this.description = command.description();
        this.imageUrl = command.imageUrl();
        this.price = command.price();
        this.pickUpAvailableDate.clear();
        this.pickUpAvailableDate.addAll(command.pickupDates());
        this.category = command.category();
    }

    private void validateProductInfo(String name, String description, String imageUrl, int price, int totalQuantity, Category category) {
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
        if (totalQuantity <= 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "총 수량은 0보다 커야 합니다.");
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
}
