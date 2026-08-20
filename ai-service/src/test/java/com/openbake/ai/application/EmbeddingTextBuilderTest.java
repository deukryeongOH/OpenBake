package com.openbake.ai.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.openbake.ai.application.EmbeddingTaskSnapshot.TaskSnapshot;
import com.openbake.ai.domain.ProductChangeType;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EmbeddingTextBuilderTest {

    private final EmbeddingTextBuilder builder = new EmbeddingTextBuilder();

    @Test
    void buildsStableKoreanTextAndSha256() {
        TaskSnapshot task = task("MEAL_BREADS");

        var result = builder.build(task);

        assertThat(result.text()).isEqualTo("""
                상품명: 고소한 통밀 식빵
                카테고리: 식사빵
                상품 유형: 일반 상품
                설명: 담백한 식사빵""");
        assertThat(result.sourceHash())
                .isEqualTo("d89885e004f5d3654d03944c3f9f76bbb12944e2a59916a3aa377387c2991cb8");
    }

    @Test
    void unknownCategoryFallsBackToEnumName() {
        var result = builder.build(task("NEW_CATEGORY"));

        assertThat(result.text()).contains("카테고리: NEW_CATEGORY");
    }

    private TaskSnapshot task(String category) {
        return new TaskSnapshot(
                1L,
                10L,
                UUID.randomUUID(),
                ProductChangeType.CREATED,
                "고소한 통밀 식빵",
                "담백한 식사빵",
                category,
                "GENERAL",
                Instant.parse("2026-08-20T00:00:00Z"),
                0);
    }
}
