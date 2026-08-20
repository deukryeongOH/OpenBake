package com.openbake.product.presentation.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;

public record RecommendationCandidateRequest(
        @NotNull @Positive Long memberId,
        @NotEmpty @Size(max = 100) List<@NotNull @Positive Long> productIds) {
}
