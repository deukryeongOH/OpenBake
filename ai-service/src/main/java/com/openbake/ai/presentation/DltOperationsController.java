package com.openbake.ai.presentation;

import com.openbake.ai.application.DltOperationsService;
import com.openbake.common.response.ApiResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/v1/ai/dlt")
@RequiredArgsConstructor
public class DltOperationsController {

    private final DltOperationsService service;

    @GetMapping
    public ApiResponse<List<DltOperationsService.DltRecord>> fetch(@RequestParam String topic) {
        return ApiResponse.ok(service.fetch(topic));
    }

    @PostMapping("/republish")
    public ApiResponse<DltOperationsService.RepublishResult> republish(
            @RequestBody List<DltOperationsService.DltSelection> records) {
        return ApiResponse.ok(service.republish(records));
    }
}
