package com.openbake.product.domain;


import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(name = "product_inventories")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductInventory {

    @Id
    @Column(name = "product_id")
    private Long productId;

    @Column(nullable = false)
    private int remainQuantity;

    @Column(nullable = false)
    private int totalQuantity;

    @Builder
    ProductInventory(int remainQuantity, int totalQuantity) {
        this.remainQuantity = remainQuantity;
        this.totalQuantity = totalQuantity;
    }
}
