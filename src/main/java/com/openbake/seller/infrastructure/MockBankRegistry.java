package com.openbake.seller.infrastructure;

import org.springframework.stereotype.Component;

import java.util.Set;

@Component
public class MockBankRegistry {

    /**
     * 은행 코드
     * 002 : 산업은행 (KDB), 003 : IBK기업은행, 004 : KB국민은행, 007 : SH수협은행
     * 011 : NH농협은행, 020 : 우리은행, 023 : SC제일은행, 027 : 한국씨티은행
     * 032 : BNK부산은행, 034 : 광주은행, 035 : 제주은행, 037 : 전북은행
     * 039 : BNK경남은행, 045 : 새마을금고, 048 : 신협, 071 : 우체국
     * 081 : 하나은행, 088 : 신한은행, 089 : 케이뱅크, 090 : 카카오뱅크
     * 092 : 토스뱅크
     */
    private static final Set<String> VALID_BANK_CODES = Set.of(
            "002", "003", "004", "007", "011", "020", "023", "027",
            "032", "034", "035", "037", "039", "045", "048", "071",
            "081", "088", "089", "090", "092"
    );

    public boolean isValidBankCode(String bankCode) {
        return VALID_BANK_CODES.contains(bankCode);
    }
}
