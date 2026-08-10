package com.openbake.drop.presentation;

import com.openbake.common.response.ApiResponse;
import com.openbake.common.security.CurrentMemberProvider;
import com.openbake.drop.application.DropEnterService;
import com.openbake.drop.application.dto.ConfirmEntryResult;
import com.openbake.drop.application.dto.QueueRankResult;
import com.openbake.drop.presentation.dto.ConfirmEntryResponse;
import com.openbake.drop.presentation.dto.QueueRankResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Drop Entry", description = "드롭 대기열 진입/순번 조회/입장 확정")
@RestController
@RequestMapping("/api/v1/drops")
@RequiredArgsConstructor
public class DropEnterController {

    private final DropEnterService dropEnterService;
    private final CurrentMemberProvider provider;

    @Operation(
            summary = "드롭 입장 확정",
            description = "대기열 통과 직후 드롭 입장을 확정합니다. 대기열에서 활성화된 상태가 아니면 실패합니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "DR009 드롭에 입장 할 수 없습니다. 조금만 더 기다려주세요."),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "DR001 존재하지 않는 드롭입니다.")
    })
    @PostMapping("/{dropId}/confirm-entry")
    public ApiResponse<ConfirmEntryResponse> enterDrop(
            @Parameter(description = "드롭 ID", example = "1") @PathVariable("dropId") Long dropId){
        Long memberId = provider.getId();
        ConfirmEntryResult result = dropEnterService.confirmEntry(dropId, memberId);
        return ApiResponse.ok(ConfirmEntryResponse.of(result));
    }


    @Operation(
            summary = "드롭 대기열 진입",
            description = "드롭 응모 대기열에 진입해 순번을 발급받습니다. 드롭 진행 기간이 아니거나 이미 참여/구매 완료한 경우 실패합니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "DR008 현재 응모 가능한 드롭 기간이 아닙니다."),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "DR001 존재하지 않는 드롭입니다."),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "DR006 이미 응모 완료된 드롭입니다.")
    })
    @PostMapping("/{dropId}/enter") // 대기열 진입 할 때
    public ApiResponse<QueueRankResponse> enterQueue(
            @Parameter(description = "드롭 ID", example = "1") @PathVariable("dropId") Long dropId){
        Long memberId = provider.getId();
        QueueRankResult result = dropEnterService.enterQueue(dropId, memberId);
        return ApiResponse.ok(QueueRankResponse.of(result));
    }

    @Operation(
            summary = "내 대기열 순번 조회",
            description = "현재 내 대기열 순번을 조회합니다. 프론트에서 폴링 방식으로 지속 호출하는 API입니다."
    )
    @GetMapping("/{dropId}/queue/rank") // 내 대기열 순번 확인 (프론트에서 지속적으로 호출)
    public ApiResponse<QueueRankResponse> getQueueRank(
            @Parameter(description = "드롭 ID", example = "1") @PathVariable("dropId") Long dropId){
        Long memberId = provider.getId();
        QueueRankResult result = dropEnterService.getRank(dropId, memberId);
        return ApiResponse.ok(QueueRankResponse.of(result));
    }

    @Operation(
            summary = "오늘 진행하는 드롭 ID List 조회",
            description = "오늘 시작하는 드롭의 ID의 List를 조회합니다. 대기열 진입 전 선행 작업으로 호출하며, 인증 없이 누구나 조회 가능한 공개 API입니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "C003 대상을 찾을 수 없습니다.")
    })
    @SecurityRequirements
    @GetMapping("/today/drops") // 오늘 진행하는 드롭ID 리스트 가져오기 (대기열 선행 작업, ID가 있어야 대기열 생성가능)
    public ApiResponse<List<Long>> getDropIds(){
        List<Long> dropIds = dropEnterService.getTodayDropIds();
        return ApiResponse.ok(dropIds);
    }
}
