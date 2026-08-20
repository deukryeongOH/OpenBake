package com.openbake.product.application.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.openbake.product.domain.Category;
import com.openbake.product.domain.Type;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ProductInfoResultTest {

    private static final LocalDate PICKUP = LocalDate.of(2026, 8, 23);

    @Test
    @DisplayName("픽업 날짜는 원본 컬렉션과 분리돼, 이후 원본이 바뀌어도 결과가 흔들리지 않는다")
    void of_detachesPickUpDatesFromSourceCollection() {
        Set<LocalDate> source = new HashSet<>(Set.of(PICKUP));

        ProductInfoResult result = create(source);

        // 지연 로딩 컬렉션이 트랜잭션 종료 후 무효화되는 상황을 원본 변경으로 흉내낸다.
        source.clear();

        assertThat(result.pickUpAvailableDates()).containsExactly(PICKUP);
    }

    @Test
    @DisplayName("원본 컬렉션 인스턴스를 그대로 들고 있지 않는다")
    void of_doesNotKeepSourceInstance() {
        Set<LocalDate> source = new HashSet<>(Set.of(PICKUP));

        ProductInfoResult result = create(source);

        assertThat(result.pickUpAvailableDates()).isNotSameAs(source);
    }

    @Test
    @DisplayName("픽업 날짜가 null이면 null을 유지한다")
    void of_keepsNullPickUpDates() {
        assertThat(create(null).pickUpAvailableDates()).isNull();
    }

    private ProductInfoResult create(Set<LocalDate> pickupDates) {
        return ProductInfoResult.of(
                "고소한 통밀 식빵", "담백한 식사빵", "https://example.com/a.png",
                20, 6500, pickupDates, Category.MEAL_BREADS,
                10L, 20, Type.GENERAL, 1L);
    }
}
