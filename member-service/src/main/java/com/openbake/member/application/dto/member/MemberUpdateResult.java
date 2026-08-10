package com.openbake.member.application.dto.member;

public record MemberUpdateResult(
        Long id,
        String name,
        String phoneNumber
) {}
