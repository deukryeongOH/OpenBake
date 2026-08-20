package com.openbake.ai.application;

import com.openbake.ai.application.EmbeddingTaskSnapshot.TaskSnapshot;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class EmbeddingTextBuilder {

    // core-service Category/Type 표시명과 의도적으로 복제한다. 변경 시 전체 재색인이 필요하다.
    private static final Map<String, String> CATEGORY_LABELS = Map.of(
            "MEAL_BREADS", "식사빵",
            "SWEET_BREADS", "간식빵",
            "CAKES_TARTS", "케이크/타르트",
            "JAM_SPREAD", "잼/스프레드",
            "COOKIES_BAKES", "쿠키/구움과자");
    private static final Map<String, String> TYPE_LABELS = Map.of(
            "GENERAL", "일반 상품",
            "DROP", "드롭 상품");

    public SourceText build(TaskSnapshot task) {
        String categoryLabel = CATEGORY_LABELS.get(task.category());
        if (categoryLabel == null) {
            categoryLabel = task.category();
            log.warn("[Embedding] 알 수 없는 카테고리, enum 이름 사용 productId={}, category={}",
                    task.productId(), task.category());
        }
        String typeLabel = TYPE_LABELS.getOrDefault(task.productType(), task.productType());
        String text = """
                상품명: %s
                카테고리: %s
                상품 유형: %s
                설명: %s""".formatted(task.name(), categoryLabel, typeLabel, task.description());
        return new SourceText(text, sha256(text));
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    public record SourceText(String text, String sourceHash) {
    }
}
