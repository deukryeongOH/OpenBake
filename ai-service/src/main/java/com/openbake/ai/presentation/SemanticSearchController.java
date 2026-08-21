package com.openbake.ai.presentation;

import com.openbake.ai.application.SemanticSearchRequest;
import com.openbake.ai.application.SemanticSearchResult;
import com.openbake.ai.application.SemanticSearchService;
import com.openbake.common.response.ApiResponse;
import com.openbake.common.security.service.CoreServicePaths;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class SemanticSearchController {

    private final SemanticSearchService semanticSearchService;

    @PostMapping(CoreServicePaths.SEMANTIC_SEARCH)
    public ApiResponse<SemanticSearchResult> search(@RequestBody SemanticSearchRequest request) {
        return ApiResponse.ok(semanticSearchService.search(request));
    }
}
