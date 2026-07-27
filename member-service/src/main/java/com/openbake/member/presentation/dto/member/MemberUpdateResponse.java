package com.openbake.member.presentation.dto.member;

public record MemberUpdateResponse(
        Long id,
        String name,
        String phoneNumber
) {}
