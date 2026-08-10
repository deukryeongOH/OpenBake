package com.openbake.seller.application;

public record AccountVerificationConfirmCommand(
        String verificationCode
) {}
