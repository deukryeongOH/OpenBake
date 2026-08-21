package com.openbake.drop.presentation.controller;

import com.openbake.common.response.ApiResponse;
import com.openbake.drop.application.service.DropEnterService;
import com.openbake.drop.application.dto.ConfirmEntryResult;
import com.openbake.drop.presentation.dto.ConfirmEntryResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Drop Entry", description = "드롭 입장 확정 / 오늘의 드롭 조회")
@RestController
@RequestMapping("/api/v1/drops")
@RequiredArgsConstructor
public class DropEnterController {

    private final DropEnterService dropEnterService;

    @Operation(
            summary = "드롭 입장",
            description = "드롭 입장을 확정합니다. 드롭 진행 기간이 아니거나 이미 재고를 선점/구매 완료한 경우 실패합니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "DR008 현재 응모 가능한 드롭 기간이 아닙니다."),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "DR001 존재하지 않는 드롭입니다."),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "DR006 이미 응모 완료된 드롭입니다.")
    })
    @PostMapping("/{dropId}/confirm-entry")
    public ApiResponse<ConfirmEntryResponse> enterDrop(
            @Parameter(description = "드롭 ID", example = "1") @PathVariable("dropId") Long dropId){
        ConfirmEntryResult result = dropEnterService.confirmEntry(dropId);
        return ApiResponse.ok(ConfirmEntryResponse.of(result));
    }

    @Operation(
            summary = "오늘 진행하는 드롭 ID List 조회",
            description = "오늘 시작하는 드롭의 ID의 List를 조회합니다. 입장 확정 전 선행 작업으로 호출하며, 인증 없이 누구나 조회 가능한 공개 API입니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "C003 대상을 찾을 수 없습니다.")
    })
    @SecurityRequirements
    @GetMapping("/today/drops") // 오늘 진행하는 드롭ID 리스트 가져오기 (입장 선행 작업)
    public ApiResponse<List<Long>> getDropIds(){
        List<Long> dropIds = dropEnterService.getTodayDropIds();
        return ApiResponse.ok(dropIds);
    }
}