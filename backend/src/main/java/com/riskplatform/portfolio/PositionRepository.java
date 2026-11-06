package com.riskplatform.portfolio;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PositionRepository extends JpaRepository<Position, Long> {
    List<Position> findByPortfolioId(Long portfolioId);

    Optional<Position> findByIdAndPortfolioId(Long id, Long portfolioId);
}
