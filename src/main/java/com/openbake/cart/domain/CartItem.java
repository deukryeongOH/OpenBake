package com.openbake.cart.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "cart_items")
@Getter
@NoArgsConstructor
public class CartItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long cartItemId;

    @OneToOne(fetch = FetchType.LAZY) //LAZY 지연로딩
    @JoinColumn(name = "cart_id", nullable = false, unique = true)
    private Cart cart;

    @Column(nullable = false)
    private Long dropId;

    @Column(nullable = false)
    private int quantity;

    //정적 팩토리 메서드
    public static CartItem create(Long dropId, int quantity) {
        CartItem item = new CartItem();
        item.dropId = dropId;
        item.quantity = quantity;
        return item;
    }

    //연관관계 편의 메서드. Cart.addItem 에서만 호출한다.
    void setCart(Cart cart) {
        this.cart = cart;
    }
}
