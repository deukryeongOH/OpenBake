package com.openbake.seller.domain;


import java.util.List;
import java.util.Optional;

public interface SellerRepository {
    Optional<Seller> findById(Long id);
    Optional<Seller> findByMemberId(Long memberId);
    List<Seller> findByApplicationStatus(ApplicationStatus applicationStatus);
    Seller save(Seller seller);
}
