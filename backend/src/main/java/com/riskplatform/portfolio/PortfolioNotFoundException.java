package com.riskplatform.portfolio;

public class PortfolioNotFoundException extends RuntimeException {
    public PortfolioNotFoundException(Long id) {
        super("Portfolio not found: " + id);
    }
}