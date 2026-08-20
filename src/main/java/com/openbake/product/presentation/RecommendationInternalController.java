package com.openbake.product.presentation;

import com.openbake.common.response.ApiResponse;
import com.openbake.product.application.RecommendationCandidateService;
import com.openbake.product.presentation.dto.RecommendationCandidateRequest;
import com.openbake.product.presentation.dto.RecommendationCandidateResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/internal/v1/products")
@RequiredArgsConstructor
public class RecommendationInternalController {

    private final RecommendationCandidateService service;

    @PostMapping("/recommendation-candidates")
    public ApiResponse<RecommendationCandidateResponse> validateCandidates(
            @Valid @RequestBody RecommendationCandidateRequest request) {
        return ApiResponse.ok(RecommendationCandidateResponse.from(
                service.validate(request.memberId(), request.productIds())));
    }

    @GetMapping("/latest-recommendation-candidates")
    public ApiResponse<RecommendationCandidateResponse> latestCandidates(
            @RequestParam @Positive Long memberId,
            @RequestParam @Min(1) @Max(100) int size) {
        return ApiResponse.ok(RecommendationCandidateResponse.from(service.latest(memberId, size)));
    }
}
