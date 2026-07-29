package com.openbake.seller.infrastructure;

import com.openbake.seller.domain.ApplicationStatus;
import com.openbake.seller.domain.Seller;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SellerJpaRepository extends JpaRepository<Seller, Long> {
    Optional<Seller> findByMemberId(Long memberId);
    List<Seller> findByApplicationStatus(ApplicationStatus applicationStatus);
}
