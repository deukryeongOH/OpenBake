package com.openbake.seller.infrastructure;

import com.openbake.seller.domain.BusinessRegistry;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class MockBusinessRegistry implements BusinessRegistry {

    private static final Map<String, String> REGISTERED_BUSINESSES = Map.of(
            "123-45-67890", "이세종",
            "111-22-33333", "홍길동"
    );

    @Override
    public boolean isRegistered(String businessNumber, String businessRepresentativeName) {
        String registeredName = REGISTERED_BUSINESSES.get(businessNumber);
        return registeredName != null && registeredName.equals(businessRepresentativeName);
    }
}
