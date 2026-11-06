package com.riskplatform.risk;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface RiskCalculationRepository extends JpaRepository<RiskCalculation, Long>,
        JpaSpecificationExecutor<RiskCalculation> {

    boolean existsByPortfolioId(Long portfolioId);
}
