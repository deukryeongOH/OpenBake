package com.openbake.seller.domain;


import java.util.Optional;

public interface SellerRepository {
    Optional<Seller> findById(Long id);
    Optional<Seller> findByMemberId(Long memberId);
    Seller save(Seller seller);
}
