package com.openbake.seller.infrastructure.crypto;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.security.SecureRandom;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AesGcmEncryptorTest {

    private AesGcmEncryptor encryptor;

    @BeforeEach
    void setUp() {
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        String encodedKey = Base64.getEncoder().encodeToString(key);
        encryptor = new AesGcmEncryptor(new SettlementEncryptionProperties(encodedKey));
    }

    @Test
    @DisplayName("암호화한 문자열을 복호화하면 원문과 같다")
    void encryptAndDecrypt_roundTrip() {
        String plainText = "110-234-567890";

        String cipherText = encryptor.encrypt(plainText);
        String decrypted = encryptor.decrypt(cipherText);

        assertThat(decrypted).isEqualTo(plainText);
    }

    @Test
    @DisplayName("같은 평문을 여러 번 암호화하면 매번 다른 암호문이 나온다 (IV 랜덤성)")
    void encrypt_producesDifferentCipherTextEachTime() {
        String plainText = "홍길동";

        String first = encryptor.encrypt(plainText);
        String second = encryptor.encrypt(plainText);

        assertThat(first).isNotEqualTo(second);
        assertThat(encryptor.decrypt(first)).isEqualTo(plainText);
        assertThat(encryptor.decrypt(second)).isEqualTo(plainText);
    }

    @Test
    @DisplayName("키 길이가 32바이트(AES-256)가 아니면 생성 시점에 예외가 발생한다")
    void constructor_rejectsInvalidKeyLength() {
        String shortKey = Base64.getEncoder().encodeToString(new byte[16]);

        assertThatThrownBy(() -> new AesGcmEncryptor(new SettlementEncryptionProperties(shortKey)))
                .isInstanceOf(IllegalStateException.class);
    }
}
