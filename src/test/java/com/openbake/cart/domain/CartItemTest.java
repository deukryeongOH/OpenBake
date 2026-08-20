package com.openbake.cart.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CartItem 엔티티 단위 테스트.
 *
 * merge 는 네 필드가 각각 다른 규칙을 따른다.
 * 수량은 더하고, 상호명과 픽업일은 값이 들어왔을 때만 덮어쓰고, 기준 가격은 항상 갱신한다.
 */
class CartItemTest {

    private static final Long PRODUCT_ID = 7L;
    private static final String BAKERY = "오픈베이크 베이커리";
    private static final LocalDate PICKUP_DATE = LocalDate.now().plusDays(7);

    private CartItem item(int quantity, LocalDate pickUpDate, Integer addedPrice) {
        return CartItem.create(PRODUCT_ID, BAKERY, quantity, pickUpDate, addedPrice);
    }

    // ---------- 가격 변동 판정 ----------

    @Test
    @DisplayName("담을 때보다 가격이 오르면 변동으로 본다")
    void isPriceChanged_whenIncreased() {
        assertThat(item(1, PICKUP_DATE, 11000).isPriceChanged(12000)).isTrue();
    }

    @Test
    @DisplayName("담을 때보다 가격이 내려도 변동으로 본다")
    void isPriceChanged_whenDecreased() {
        assertThat(item(1, PICKUP_DATE, 15000).isPriceChanged(12000)).isTrue();
    }

    @Test
    @DisplayName("가격이 그대로면 변동이 아니다")
    void isPriceChanged_whenSame() {
        assertThat(item(1, PICKUP_DATE, 12000).isPriceChanged(12000)).isFalse();
    }

    @Test
    @DisplayName("기준 가격이 없으면 비교할 수 없으므로 변동이 아니다")
    void isPriceChanged_whenAddedPriceIsNull() {
        // 컬럼이 생기기 전에 담긴 행이다.
        assertThat(item(1, PICKUP_DATE, null).isPriceChanged(12000)).isFalse();
    }

    @Test
    @DisplayName("기준 가격 비교는 참조가 아니라 값으로 한다")
    void isPriceChanged_comparesByValue() {
        // Integer 캐시 범위(-128~127) 밖이라 참조 비교였다면 여기서 깨진다.
        assertThat(item(1, PICKUP_DATE, 12000).isPriceChanged(12000)).isFalse();
        assertThat(item(1, PICKUP_DATE, 12000).isPriceChanged(12001)).isTrue();
    }

    // ---------- 다시 담기 ----------

    @Test
    @DisplayName("다시 담으면 수량이 더해진다")
    void merge_addsQuantity() {
        // given
        CartItem item = item(2, PICKUP_DATE, 12000);

        // when
        item.merge(BAKERY, 3, PICKUP_DATE, 12000);

        // then
        assertThat(item.getQuantity()).isEqualTo(5);
    }

    @Test
    @DisplayName("픽업일을 새로 고르면 그 값으로 덮어쓴다")
    void merge_overwritesPickUpDateWhenChosen() {
        // given
        CartItem item = item(1, PICKUP_DATE, 12000);
        LocalDate newDate = PICKUP_DATE.plusDays(1);

        // when
        item.merge(BAKERY, 1, newDate, 12000);

        // then
        assertThat(item.getPickUpDate()).isEqualTo(newDate);
    }

    @Test
    @DisplayName("픽업일을 고르지 않으면 기존 선택을 지우지 않는다")
    void merge_keepsPickUpDateWhenNull() {
        // given
        CartItem item = item(1, PICKUP_DATE, 12000);

        // when — 수량만 고르고 담은 경우다.
        item.merge(BAKERY, 1, null, 12000);

        // then
        assertThat(item.getPickUpDate()).isEqualTo(PICKUP_DATE);
    }

    @Test
    @DisplayName("픽업일이 없던 항목은 이번에 고른 날짜로 채워진다")
    void merge_fillsPickUpDateWhenAbsent() {
        // given
        CartItem item = item(1, null, 12000);

        // when
        item.merge(BAKERY, 1, PICKUP_DATE, 12000);

        // then
        assertThat(item.getPickUpDate()).isEqualTo(PICKUP_DATE);
    }

    @Test
    @DisplayName("기준 가격은 조건 없이 이번 가격으로 갱신된다")
    void merge_alwaysRefreshesAddedPrice() {
        // given — 11000 원에 담아둔 항목
        CartItem item = item(1, PICKUP_DATE, 11000);

        // when — 12000 원으로 오른 뒤 다시 담는다.
        item.merge(BAKERY, 1, null, 12000);

        // then — 방금 12000 원을 보고 담았으므로 더 이상 변동으로 알리지 않는다.
        assertThat(item.getAddedPrice()).isEqualTo(12000);
        assertThat(item.isPriceChanged(12000)).isFalse();
    }

    @Test
    @DisplayName("다시 담으면 상호명도 이번에 읽은 값으로 갱신된다")
    void merge_refreshesBakeryName() {
        // given — 옛 상호로 담아둔 항목
        CartItem item = CartItem.create(PRODUCT_ID, "옛날 베이커리", 1, PICKUP_DATE, 12000);

        // when — 판매자가 상호를 바꾼 뒤 다시 담는다.
        item.merge("새이름 베이커리", 1, PICKUP_DATE, 12000);

        // then — 상품이 삭제됐을 때 쓰는 단서이므로 최근에 확인한 이름일수록 정확하다.
        assertThat(item.getBakeryName()).isEqualTo("새이름 베이커리");
    }

    @Test
    @DisplayName("판매자를 찾지 못했으면 기존 상호명을 지우지 않는다")
    void merge_keepsBakeryNameWhenNull() {
        // given
        CartItem item = item(1, PICKUP_DATE, 12000);

        // when — 판매자 조회에 실패해 상호명이 비어 온 경우다.
        item.merge(null, 1, PICKUP_DATE, 12000);

        // then — 남아 있는 단서를 지우면 삭제된 상품 행에 보여줄 게 없어진다.
        assertThat(item.getBakeryName()).isEqualTo(BAKERY);
    }

    // ---------- 개별 변경 ----------

    @Test
    @DisplayName("수량 변경은 더하는 게 아니라 교체다")
    void updateQuantity_replaces() {
        // given
        CartItem item = item(3, PICKUP_DATE, 12000);

        // when
        item.updateQuantity(5);

        // then
        assertThat(item.getQuantity()).isEqualTo(5);
    }

    @Test
    @DisplayName("픽업일은 다시 고르면 덮어쓴다")
    void updatePickUpDate_overwrites() {
        // given
        CartItem item = item(1, null, 12000);

        // when
        item.updatePickUpDate(PICKUP_DATE);

        // then
        assertThat(item.getPickUpDate()).isEqualTo(PICKUP_DATE);
    }
}
