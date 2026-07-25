package com.openbake.seller.infrastructure.crypto;

import com.openbake.seller.domain.SettlementEncryptor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES-256-GCM 대칭키 암복호화기.
 * 매 암호화마다 랜덤 IV를 생성해 (IV + 암호문) 순으로 이어붙인 뒤 Base64로 인코딩한다.
 */
@Component
@EnableConfigurationProperties(SettlementEncryptionProperties.class)
public class AesGcmEncryptor implements SettlementEncryptor {

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_LENGTH_BYTES = 12;
    private static final int TAG_LENGTH_BITS = 128;
    private static final int KEY_LENGTH_BYTES = 32;

    private final SecretKeySpec secretKey;
    private final SecureRandom secureRandom = new SecureRandom();

    public AesGcmEncryptor(SettlementEncryptionProperties properties) {
        byte[] keyBytes = Base64.getDecoder().decode(properties.encryptionKey());
        if (keyBytes.length != KEY_LENGTH_BYTES) {
            throw new IllegalStateException(
                    "seller.settlement.encryption-key는 Base64로 인코딩된 32바이트(AES-256) 키여야 합니다.");
        }
        this.secretKey = new SecretKeySpec(keyBytes, "AES");
    }

    @Override
    public String encrypt(String plainText) {
        if (plainText == null) {
            return null;
        }
        try {
            byte[] iv = new byte[IV_LENGTH_BYTES];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            byte[] cipherText = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));

            return Base64.getEncoder().encodeToString(
                    ByteBuffer.allocate(iv.length + cipherText.length)
                            .put(iv)
                            .put(cipherText)
                            .array());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("정산 계좌 정보 암호화에 실패했습니다.", e);
        }
    }

    @Override
    public String decrypt(String encoded) {
        if (encoded == null) {
            return null;
        }
        try {
            ByteBuffer buffer = ByteBuffer.wrap(Base64.getDecoder().decode(encoded));
            byte[] iv = new byte[IV_LENGTH_BYTES];
            buffer.get(iv);
            byte[] cipherText = new byte[buffer.remaining()];
            buffer.get(cipherText);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            return new String(cipher.doFinal(cipherText), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("정산 계좌 정보 복호화에 실패했습니다.", e);
        }
    }
}
