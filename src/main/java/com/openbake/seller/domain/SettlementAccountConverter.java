package com.openbake.seller.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.springframework.stereotype.Component;

/**
 * Seller의 정산 계좌 정보를 DB 저장 시 암호화, 조회 시 복호화한다.
 * Spring Boot가 EntityManagerFactory에 SpringBeanContainer를 등록해주므로
 * {@code @Component}로 등록된 이 컨버터는 Hibernate가 스프링 빈으로 조회해 주입한다.
 */
@Converter
@Component
public class SettlementAccountConverter implements AttributeConverter<String, String> {

    private final SettlementEncryptor settlementEncryptor;

    public SettlementAccountConverter(SettlementEncryptor settlementEncryptor) {
        this.settlementEncryptor = settlementEncryptor;
    }

    @Override
    public String convertToDatabaseColumn(String attribute) {
        return settlementEncryptor.encrypt(attribute);
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        return settlementEncryptor.decrypt(dbData);
    }
}
