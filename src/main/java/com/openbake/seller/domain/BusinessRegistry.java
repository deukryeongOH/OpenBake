package com.openbake.seller.domain;

public interface BusinessRegistry {
    boolean isRegistered(String businessNumber, String businessRepresentativeName);
}
