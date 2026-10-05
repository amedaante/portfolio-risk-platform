package com.riskplatform.risk;

import com.riskplatform.risk.dto.RiskCalculationDtos.HistoricalVarCalculationResponse;
import com.riskplatform.risk.dto.QuantEngineDtos.RiskContributionResponse;
import com.riskplatform.risk.dto.QuantEngineDtos.StressScenarioResponse;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

@RestController
public class RiskCalculationController {

    private final RiskCalculationService riskCalculationService;

    public RiskCalculationController(RiskCalculationService riskCalculationService) {
        this.riskCalculationService = riskCalculationService;
    }

    @PostMapping("/api/portfolios/{portfolioId}/risk-calculations/historical-var")
    public HistoricalVarCalculationResponse calculateHistoricalVar(
            @PathVariable Long portfolioId,
            @RequestParam(defaultValue = "0.95") BigDecimal confidenceLevel,
            @RequestParam(defaultValue = "1") Integer horizonDays) {

        return riskCalculationService.calculateHistoricalVar(portfolioId, confidenceLevel, horizonDays);
    }

    @PostMapping("/api/portfolios/{portfolioId}/risk-calculations/parametric-var")
    public HistoricalVarCalculationResponse calculateParametricVar(
            @PathVariable Long portfolioId,
            @RequestParam(defaultValue = "0.95") BigDecimal confidenceLevel,
            @RequestParam(defaultValue = "1") Integer horizonDays) {

        return riskCalculationService.calculateParametricVar(portfolioId, confidenceLevel, horizonDays);
    }

    @PostMapping("/api/portfolios/{portfolioId}/risk-calculations/monte-carlo-var")
    public HistoricalVarCalculationResponse calculateMonteCarloVar(
            @PathVariable Long portfolioId,
            @RequestParam(defaultValue = "0.95") BigDecimal confidenceLevel,
            @RequestParam(defaultValue = "1") Integer horizonDays,
            @RequestParam(defaultValue = "10000") Integer numSimulations,
            @RequestParam(defaultValue = "42") Long randomSeed) {

        return riskCalculationService.calculateMonteCarloVar(
                portfolioId, confidenceLevel, horizonDays, numSimulations, randomSeed);
    }

    @PostMapping("/api/portfolios/{portfolioId}/risk-calculations/risk-contribution")
    public RiskContributionResponse calculateRiskContribution(
            @PathVariable Long portfolioId,
            @RequestParam(defaultValue = "0.95") BigDecimal confidenceLevel,
            @RequestParam(defaultValue = "1") Integer horizonDays) {

        return riskCalculationService.calculateRiskContribution(portfolioId, confidenceLevel, horizonDays);
    }

    @PostMapping("/api/portfolios/{portfolioId}/risk-calculations/stress-test")
    public StressScenarioResponse runStressTest(
            @PathVariable Long portfolioId,
            @RequestParam String scenarioName,
            @RequestParam(defaultValue = "0") BigDecimal equityShockPct,
            @RequestParam(defaultValue = "0") BigDecimal rateShockBps,
            @RequestParam(defaultValue = "0") BigDecimal fxShockPct) {

        return riskCalculationService.runStressTest(
                portfolioId, scenarioName, equityShockPct, rateShockBps, fxShockPct);
    }

    @GetMapping("/api/risk-calculations/{id}")
    public HistoricalVarCalculationResponse getCalculation(@PathVariable Long id) {
        return riskCalculationService.getCalculation(id);
    }
}