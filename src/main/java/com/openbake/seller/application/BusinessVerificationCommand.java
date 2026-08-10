package com.openbake.seller.application;

public record BusinessVerificationCommand(
        String businessNumber,
        String businessAddress,
        String businessRepresentativeName
) {}
