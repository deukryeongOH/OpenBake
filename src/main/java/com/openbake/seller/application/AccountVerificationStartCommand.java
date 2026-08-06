package com.openbake.seller.application;

public record AccountVerificationStartCommand(
        String bankCode,
        String accountNumber,
        String accountHolder
) {}
