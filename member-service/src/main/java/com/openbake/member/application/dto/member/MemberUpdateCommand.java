package com.openbake.member.application.dto.member;

public record MemberUpdateCommand(
        String name,
        String phoneNumber
) {}
