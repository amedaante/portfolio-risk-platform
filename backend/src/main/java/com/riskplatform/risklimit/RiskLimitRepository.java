package com.riskplatform.risklimit;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RiskLimitRepository extends JpaRepository<RiskLimit, Long> {
    List<RiskLimit> findByPortfolioId(Long portfolioId);
}