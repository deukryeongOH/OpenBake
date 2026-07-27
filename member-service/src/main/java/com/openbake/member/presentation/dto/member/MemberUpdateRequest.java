package com.openbake.member.presentation.dto.member;

import jakarta.validation.constraints.Size;

public record MemberUpdateRequest(
        @Size(min = 1) String name,
        @Size(min = 1) String phoneNumber
) {}
