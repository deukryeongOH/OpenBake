package com.openbake.seller.infrastructure;

import com.openbake.seller.domain.Seller;
import com.openbake.seller.domain.SellerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class SellerRepositoryImpl implements SellerRepository {

    private final SellerJpaRepository  sellerJpaRepository;

    @Override
    public Optional<Seller> findById(Long id) {
        return sellerJpaRepository.findById(id);
    }

    @Override
    public Optional<Seller> findByMemberId(Long memberId) {
        return sellerJpaRepository.findByMemberId(memberId);
    }

    @Override
    public Seller save(Seller seller) {
        return sellerJpaRepository.save(seller);
    }

}
