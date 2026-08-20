package com.openbake.drop.presentation.controller;


import com.openbake.common.response.ApiResponse;
import com.openbake.drop.application.dto.*;
import com.openbake.drop.application.service.DropService;
import com.openbake.drop.application.service.DropDetailService;
import com.openbake.drop.presentation.dto.DropInfoResponse;
import com.openbake.drop.presentation.dto.DropInfoRequest;
import com.openbake.drop.presentation.dto.LocalDateRequest;
import com.openbake.drop.presentation.dto.TimeSlotResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Drop", description = "드롭(상품) 등록·조회·수정·삭제, 판매자 본인 드롭 목록 조회")
@RestController
@RequestMapping("/api/v1/drops")
@RequiredArgsConstructor
public class DropController {

    private final DropService dropService;
    private final DropDetailService dropDetailService;

    @Operation(
            summary = "드롭 등록",
            description = "판매자가 신규 드롭(상품)을 등록합니다. 판매자는 하루에 하나의 드롭만 등록할 수 있으며 시스템 전체 하루 5개 등록 가능합니다. 1인당 제한 수량은 총 수량을 초과할 수 없습니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "C001 잘못된 요청입니다. / DR002 드롭 시작 시간 또는 마감 시간이 유효하지 않습니다. / DR003 픽업 가능 날짜는 드롭 마감일 이후여야 합니다. / DR005 1인당 제한 수량은 총 수량보다 클 수 없습니다."),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "C002 처리할 수 없는 상태입니다. / DR004 해당 날짜에는 이미 등록된 드롭이 존재합니다.")
    })
    @PostMapping("/register")
    public ApiResponse<DropInfoResponse> registerDrop(@Valid @RequestBody DropInfoRequest dropProductInfoRequest
    ) {
        DropInfoCommand command = DropInfoCommand.create(dropProductInfoRequest.name(), dropProductInfoRequest.description(),
                dropProductInfoRequest.imageUrl(), dropProductInfoRequest.dropStart(), dropProductInfoRequest.dropStart().plusMinutes(60), dropProductInfoRequest.totalQuantity(),
                dropProductInfoRequest.limitQuantity(), dropProductInfoRequest.price(), dropProductInfoRequest.pickUpAvailableDates(), dropProductInfoRequest.category());

        DropInfoResult result = dropService.registerDrop(command);
        return ApiResponse.ok(DropInfoResponse.of(result));
    }


    @Operation(
            summary = "드롭 상세 조회",
            description = "드롭 ID로 상품 정보, 진행 상태, 재고 현황을 조회합니다. 인증 없이 누구나 조회 가능한 공개 API입니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "DR001 존재하지 않는 드롭입니다.")
    })
    @SecurityRequirements
    @GetMapping("/{dropId}/info")
    public ApiResponse<DropInfoResponse> getDropInfo(
            @Parameter(description = "조회할 드롭 ID", example = "1") @PathVariable("dropId") Long dropId){
        DropInfoResult info = dropDetailService.get(dropId);
        return ApiResponse.ok(DropInfoResponse.of(info));
    }

    @Operation(
            summary = "내 드롭 목록 조회",
            description = "판매자 본인이 등록한 드롭 목록을 전체 조회합니다."
    )
    // 판매자 본인이 등록한 드롭 목록 조회
    @GetMapping("/mine")
    public ApiResponse<List<DropInfoResponse>> getMyDrops() {
        List<DropInfoResult> results = dropService.getMyDrops();
        return ApiResponse.ok(results.stream().map(DropInfoResponse::of).toList());
    }

    @Operation(
            summary = "예정된 드롭 목록 조회 (날짜별)",
            description = "오늘부터 지정한 일수(days) 동안 시작 전(UPCOMING) 또는 진행 중(ACTIVE)인 드롭을 dropStart 오름차순으로 조회합니다. days를 생략하면 7일치를 조회합니다."
    )
    @GetMapping("/upcoming")
    public ApiResponse<List<DropInfoResponse>> getUpcomingDrops(
            @Parameter(description = "오늘부터 조회할 일수", example = "7")
            @RequestParam(defaultValue = "7") int days) {
        List<DropInfoResult> results = dropService.getUpcomingDrops(days);
        return ApiResponse.ok(results.stream().map(DropInfoResponse::of).toList());
    }

    @Operation(
            summary = "드롭 수정",
            description = "판매자 본인이 등록한 드롭을 수정합니다. 아직 시작되지 않은 드롭만 수정할 수 있습니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "C001 잘못된 요청입니다. / DR002 드롭 시작 시간 또는 마감 시간이 유효하지 않습니다. / DR003 픽업 가능 날짜는 드롭 마감일 이후여야 합니다. / DR005 1인당 제한 수량은 총 수량보다 클 수 없습니다."),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "DR016 본인이 등록한 드롭이 아닙니다."),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "DR001 존재하지 않는 드롭입니다."),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "C002 처리할 수 없는 상태입니다. / DR004 해당 날짜에는 이미 등록된 드롭이 존재합니다. / DR017 이미 시작되었거나 종료된 드롭은 수정/삭제할 수 없습니다.")
    })
    // 판매자 본인의 드롭 수정 (시작 전인 드롭만 가능)
    @PatchMapping("/{dropId}")
    public ApiResponse<DropInfoResponse> updateDropProduct(
            @Parameter(description = "수정할 드롭 ID", example = "1") @PathVariable("dropId") Long dropId,
            @Valid @RequestBody DropInfoRequest dropInfoRequest) {
        DropInfoCommand command = DropInfoCommand.create(dropInfoRequest.name(), dropInfoRequest.description(),
                dropInfoRequest.imageUrl(), dropInfoRequest.dropStart(), dropInfoRequest.dropStart().plusMinutes(60), dropInfoRequest.totalQuantity(),
                dropInfoRequest.limitQuantity(), dropInfoRequest.price(), dropInfoRequest.pickUpAvailableDates(), dropInfoRequest.category());

        DropInfoResult result = dropService.updateDropProduct(dropId, command);
        return ApiResponse.ok(DropInfoResponse.of(result));
    }

    @Operation(
            summary = "드롭 삭제",
            description = "판매자 본인이 등록한 드롭을 삭제합니다. 아직 시작되지 않은 드롭만 삭제할 수 있습니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "DR016 본인이 등록한 드롭이 아닙니다."),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "DR001 존재하지 않는 드롭입니다."),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "DR017 이미 시작되었거나 종료된 드롭은 수정/삭제할 수 없습니다.")
    })
    // 판매자 본인의 드롭 삭제 (시작 전인 드롭만 가능)
    @DeleteMapping("/{dropId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteProduct(
            @Parameter(description = "삭제할 드롭 ID", example = "1") @PathVariable("dropId") Long dropId) {
        dropService.deleteProduct(dropId);
    }

    @Operation(
            summary = "예약 가능 시간대 조회",
            description = "지정한 날짜에 아직 다른 드롭이 등록되지 않은 예약 가능한 시간대 목록을 조회합니다."
    )
    @SecurityRequirements
    @GetMapping("/available-slots")
    public ApiResponse<List<TimeSlotResponse>> getAvailableSlots(@Valid @RequestBody LocalDateRequest request){
        LocalDateCommand command = LocalDateCommand.of(request.date());
        List<TimeSlotResult> availableList = dropService.getAvailableSlots(command);

        return ApiResponse.ok(TimeSlotResponse.of(availableList));
    }

}
