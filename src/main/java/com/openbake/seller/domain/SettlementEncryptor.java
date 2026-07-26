package com.openbake.seller.domain;

/**
 * 정산 계좌 정보(계좌번호/예금주) 암복호화 포트.
 * 구현체는 infrastructure 계층에 위치한다.
 */
public interface SettlementEncryptor {

    String encrypt(String plainText);

    String decrypt(String cipherText);
}
