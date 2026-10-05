package com.riskplatform.risklimit;

import com.riskplatform.risklimit.dto.RiskLimitDtos.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/portfolios/{portfolioId}/risk-limits")
public class RiskLimitController {

    private final RiskLimitService riskLimitService;

    public RiskLimitController(RiskLimitService riskLimitService) {
        this.riskLimitService = riskLimitService;
    }

    @PostMapping
    public ResponseEntity<RiskLimitResponse> createLimit(
            @PathVariable Long portfolioId,
            @Valid @RequestBody CreateRiskLimitRequest request) {
        RiskLimitResponse response = riskLimitService.createLimit(portfolioId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public List<RiskLimitResponse> listLimits(@PathVariable Long portfolioId) {
        return riskLimitService.listLimits(portfolioId);
    }

    @DeleteMapping("/{limitId}")
    public ResponseEntity<Void> deleteLimit(@PathVariable Long portfolioId, @PathVariable Long limitId) {
        riskLimitService.deleteLimit(limitId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/evaluate")
    public PortfolioLimitEvaluationResponse evaluate(@PathVariable Long portfolioId) {
        return riskLimitService.evaluateLimits(portfolioId);
    }
}