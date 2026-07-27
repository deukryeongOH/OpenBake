package com.openbake.member.presentation;

import com.openbake.common.response.ApiResponse;
import com.openbake.member.application.MemberService;
import com.openbake.member.presentation.dto.member.MemberResponse;
import com.openbake.member.presentation.dto.member.MemberUpdateRequest;
import com.openbake.member.presentation.dto.member.MemberUpdateResponse;
import com.openbake.member.presentation.dto.member.PasswordChangeRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Member", description = "회원 조회 / 수정 / 탈퇴")
@RestController
@RequestMapping("/api/v1/members")
@RequiredArgsConstructor
public class MemberController {

    private final MemberService memberService;

    @Operation(
            summary = "회원 조회",
            description = "회원 ID로 상세 정보를 조회합니다. 본인 또는 admin만 조회 가능합니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "ME004 권한이 없습니다. (본인/admin이 아닌 접근)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "C003 대상을 찾을 수 없습니다.")
    })
    @GetMapping("/{id}")
    public ApiResponse<MemberResponse> getMember(
            @Parameter(description = "조회할 회원 ID", example = "1") @PathVariable Long id) {
        return ApiResponse.ok(memberService.getMemberById(id));
    }

    @Operation(
            summary = "회원정보 수정",
            description = "이름, 전화번호를 수정합니다. 본인만 가능하며 이메일은 수정 대상이 아닙니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "C001 잘못된 요청입니다."),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "ME004 권한이 없습니다. (본인이 아닌 회원 ID로 요청)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "C003 대상을 찾을 수 없습니다.")
    })
    @PatchMapping("/{id}")
    public ApiResponse<MemberUpdateResponse> updateMember(
            @Parameter(description = "대상 회원 ID (본인 ID와 일치해야 함)", example = "1") @PathVariable Long id,
            @Valid @RequestBody MemberUpdateRequest request) {
        return ApiResponse.ok(memberService.updateMember(id, request));
    }

    @Operation(
            summary = "비밀번호 변경",
            description = "현재 비밀번호를 확인한 뒤 새 비밀번호로 변경합니다. LOCAL 계정에만 적용되며, GOOGLE 전용 회원은 사용할 수 없습니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "C001 잘못된 요청입니다."),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "ME003 현재 비밀번호가 일치하지 않습니다."),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "ME004 권한이 없습니다. (본인이 아니거나 GOOGLE 전용 회원)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "C003 대상을 찾을 수 없습니다.")
    })
    @PatchMapping("/{id}/password")
    public ApiResponse<Void> changePassword(
            @Parameter(description = "대상 회원 ID (본인 ID와 일치해야 함)", example = "1") @PathVariable Long id,
            @Valid @RequestBody PasswordChangeRequest request) {
        memberService.changePassword(id, request);
        return ApiResponse.ok();
    }

    @Operation(
            summary = "회원 탈퇴",
            description = "본인 계정을 탈퇴 처리합니다(Soft delete). access/refresh 토큰이 즉시 무효화됩니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "ME004 권한이 없습니다. (본인이 아닌 회원 ID로 요청)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "C003 대상을 찾을 수 없습니다."),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "C002 처리할 수 없는 상태입니다. (이미 탈퇴 처리된 회원)")
    })
    @DeleteMapping("/{id}")
    public ApiResponse<Void> withdrawMember(
            @Parameter(description = "탈퇴할 회원 ID (본인 ID와 일치해야 함)", example = "1") @PathVariable Long id) {
        memberService.withdrawMember(id);
        return ApiResponse.ok();
    }

}
