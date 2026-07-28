package com.openbake.member.presentation.dto.member;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

public record MemberUpdateRequest(
        @Schema(description = "변경할 이름 (선택)", example = "이세종")
        @Size(min = 1) String name,
        @Schema(description = "변경할 전화번호 (선택)", example = "010-9999-8888")
        @Size(min = 1) String phoneNumber
) {}
