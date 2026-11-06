package com.riskplatform.portfolio;

import com.riskplatform.common.ApiException;
import org.springframework.http.HttpStatus;

public class PortfolioNotFoundException extends ApiException {
    public PortfolioNotFoundException(Long id) {
        super(HttpStatus.NOT_FOUND, "Portfolio not found: " + id);
    }
}
