package com.openbake.seller.application;

public record ApplicationCreateCommand(
        String bakeryName,
        String businessNumber,
        String businessAddress,
        String businessRepresentativeName
) {}
