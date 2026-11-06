package com.riskplatform.portfolio;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PortfolioRepository extends JpaRepository<Portfolio, Long> {

    // Fetch positions in the same query. Listing portfolios computes each
    // one's total value, which otherwise lazy-loads positions once per
    // portfolio (the N+1 query problem).
    @Override
    @EntityGraph(attributePaths = "positions")
    List<Portfolio> findAll();
}
