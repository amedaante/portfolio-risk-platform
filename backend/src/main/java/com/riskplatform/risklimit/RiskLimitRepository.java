package com.riskplatform.risklimit;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RiskLimitRepository extends JpaRepository<RiskLimit, Long> {
    List<RiskLimit> findByPortfolioId(Long portfolioId);

    Optional<RiskLimit> findByIdAndPortfolioId(Long id, Long portfolioId);
}
