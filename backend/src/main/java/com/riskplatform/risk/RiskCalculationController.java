package com.riskplatform.risk;

import com.riskplatform.risk.dto.RiskCalculationDtos.HistoricalVarCalculationResponse;
import com.riskplatform.risk.dto.QuantEngineDtos.RiskContributionResponse;
import com.riskplatform.risk.dto.QuantEngineDtos.StressScenarioResponse;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

/**
 * Parameter constraints are enforced by Spring 6.1's built-in method
 * validation (no @Validated needed); violations become 400s in
 * GlobalExceptionHandler. Without them, confidenceLevel=1.0 reached the
 * engine, produced an infinite z-score, and surfaced as a 502.
 */
@RestController
public class RiskCalculationController {

    private final RiskCalculationService riskCalculationService;

    public RiskCalculationController(RiskCalculationService riskCalculationService) {
        this.riskCalculationService = riskCalculationService;
    }

    @PostMapping("/api/portfolios/{portfolioId}/risk-calculations/historical-var")
    public HistoricalVarCalculationResponse calculateHistoricalVar(
            @PathVariable Long portfolioId,
            @RequestParam(defaultValue = "0.95") @DecimalMin(value = "0.5", inclusive = false)
            @DecimalMax(value = "1", inclusive = false) BigDecimal confidenceLevel,
            @RequestParam(defaultValue = "1") @Min(1) @Max(250) Integer horizonDays) {

        return riskCalculationService.calculateHistoricalVar(portfolioId, confidenceLevel, horizonDays);
    }

    @PostMapping("/api/portfolios/{portfolioId}/risk-calculations/parametric-var")
    public HistoricalVarCalculationResponse calculateParametricVar(
            @PathVariable Long portfolioId,
            @RequestParam(defaultValue = "0.95") @DecimalMin(value = "0.5", inclusive = false)
            @DecimalMax(value = "1", inclusive = false) BigDecimal confidenceLevel,
            @RequestParam(defaultValue = "1") @Min(1) @Max(250) Integer horizonDays) {

        return riskCalculationService.calculateParametricVar(portfolioId, confidenceLevel, horizonDays);
    }

    @PostMapping("/api/portfolios/{portfolioId}/risk-calculations/monte-carlo-var")
    public HistoricalVarCalculationResponse calculateMonteCarloVar(
            @PathVariable Long portfolioId,
            @RequestParam(defaultValue = "0.95") @DecimalMin(value = "0.5", inclusive = false)
            @DecimalMax(value = "1", inclusive = false) BigDecimal confidenceLevel,
            @RequestParam(defaultValue = "1") @Min(1) @Max(250) Integer horizonDays,
            @RequestParam(defaultValue = "10000") @Min(1000) @Max(1_000_000) Integer numSimulations,
            @RequestParam(defaultValue = "42") Long randomSeed) {

        return riskCalculationService.calculateMonteCarloVar(
                portfolioId, confidenceLevel, horizonDays, numSimulations, randomSeed);
    }

    @PostMapping("/api/portfolios/{portfolioId}/risk-calculations/risk-contribution")
    public RiskContributionResponse calculateRiskContribution(
            @PathVariable Long portfolioId,
            @RequestParam(defaultValue = "0.95") @DecimalMin(value = "0.5", inclusive = false)
            @DecimalMax(value = "1", inclusive = false) BigDecimal confidenceLevel,
            @RequestParam(defaultValue = "1") @Min(1) @Max(250) Integer horizonDays) {

        return riskCalculationService.calculateRiskContribution(portfolioId, confidenceLevel, horizonDays);
    }

    @PostMapping("/api/portfolios/{portfolioId}/risk-calculations/stress-test")
    public StressScenarioResponse runStressTest(
            @PathVariable Long portfolioId,
            @RequestParam @NotBlank @Size(max = 100) String scenarioName,
            @RequestParam(defaultValue = "0") @DecimalMin("-1") BigDecimal equityShockPct,
            @RequestParam(defaultValue = "0") @DecimalMin("-2000") @DecimalMax("2000") BigDecimal rateShockBps,
            @RequestParam(defaultValue = "0") @DecimalMin(value = "-1", inclusive = false) BigDecimal fxShockPct) {

        return riskCalculationService.runStressTest(
                portfolioId, scenarioName, equityShockPct, rateShockBps, fxShockPct);
    }

    @GetMapping("/api/risk-calculations/{id}")
    public HistoricalVarCalculationResponse getCalculation(@PathVariable Long id) {
        return riskCalculationService.getCalculation(id);
    }
}
