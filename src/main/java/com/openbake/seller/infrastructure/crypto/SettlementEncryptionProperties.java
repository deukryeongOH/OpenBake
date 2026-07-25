package com.openbake.seller.infrastructure.crypto;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "seller.settlement")
public record SettlementEncryptionProperties(String encryptionKey) {
}
