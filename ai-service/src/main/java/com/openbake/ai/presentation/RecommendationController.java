package com.openbake.ai.presentation;

import com.openbake.ai.application.RecommendationResult;
import com.openbake.ai.application.RecommendationService;
import com.openbake.common.response.ApiResponse;
import com.openbake.common.security.CurrentMemberProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/recommendations")
@RequiredArgsConstructor
public class RecommendationController {

    private final RecommendationService recommendationService;
    private final CurrentMemberProvider currentMemberProvider;

    @GetMapping
    public ApiResponse<RecommendationResult> recommendations(
            @RequestParam(required = false) Integer size) {
        return ApiResponse.ok(recommendationService.recommend(currentMemberProvider.getId(), size));
    }
}
