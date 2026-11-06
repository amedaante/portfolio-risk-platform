package com.riskplatform.risk;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.riskplatform.risk.RiskCalculationDataService.RiskCalculationInput;
import com.riskplatform.risk.dto.QuantEngineDtos.*;
import com.riskplatform.risk.dto.RiskCalculationDtos.HistoricalVarCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * Orchestrates a risk calculation in three steps:
 *   1. load inputs       (short read-only transaction, in RiskCalculationDataService)
 *   2. call quant engine (no transaction, no DB connection held)
 *   3. save the result   (short write transaction, in RiskCalculationDataService)
 *
 * Deliberately no @Transactional here. Steps 1 and 3 live on a separate
 * bean so their transactions go through Spring's proxy. Together with
 * spring.jpa.open-in-view=false, no connection is held during step 2.
 */
@Service
public class RiskCalculationService {

    private static final Logger logger = LoggerFactory.getLogger(RiskCalculationService.class);

    private final RiskCalculationDataService dataService;
    private final QuantEngineClient quantEngineClient;
    private final ObjectMapper objectMapper;

    // Spring Boot's ObjectMapper, which has JavaTimeModule registered. A
    // hand-built `new ObjectMapper()` can't serialise LocalDate, which made
    // every request_params audit value silently NULL.
    public RiskCalculationService(RiskCalculationDataService dataService,
                                  QuantEngineClient quantEngineClient,
                                  ObjectMapper objectMapper) {
        this.dataService = dataService;
        this.quantEngineClient = quantEngineClient;
        this.objectMapper = objectMapper;
    }

    public HistoricalVarCalculationResponse calculateHistoricalVar(
            Long portfolioId, BigDecimal confidenceLevel, Integer horizonDays) {

        RiskCalculationInput input = dataService.loadInput(portfolioId);
        HistoricalVarRequest request = new HistoricalVarRequest(confidenceLevel, horizonDays, input.quantPositions());
        HistoricalVarResponse result = quantEngineClient.calculateHistoricalVar(request);

        return save(portfolioId, input, result.methodology(), confidenceLevel, horizonDays,
                result.portfolioValue(), result.var(), result.expectedShortfall(),
                result.observationCount(), request);
    }

    public HistoricalVarCalculationResponse calculateParametricVar(
            Long portfolioId, BigDecimal confidenceLevel, Integer horizonDays) {

        RiskCalculationInput input = dataService.loadInput(portfolioId);
        HistoricalVarRequest request = new HistoricalVarRequest(confidenceLevel, horizonDays, input.quantPositions());
        ParametricVarResponse result = quantEngineClient.calculateParametricVar(request);

        return save(portfolioId, input, result.methodology(), confidenceLevel, horizonDays,
                result.portfolioValue(), result.var(), result.expectedShortfall(),
                result.observationCount(), request);
    }

    public HistoricalVarCalculationResponse calculateMonteCarloVar(
            Long portfolioId, BigDecimal confidenceLevel, Integer horizonDays,
            Integer numSimulations, Long randomSeed) {

        RiskCalculationInput input = dataService.loadInput(portfolioId);
        MonteCarloVarRequest request = new MonteCarloVarRequest(
                confidenceLevel, horizonDays, numSimulations, randomSeed, input.quantPositions());
        MonteCarloVarResponse result = quantEngineClient.calculateMonteCarloVar(request);

        return save(portfolioId, input, result.methodology(), confidenceLevel, horizonDays,
                result.portfolioValue(), result.var(), result.expectedShortfall(),
                result.observationCount(), request);
    }

    public RiskContributionResponse calculateRiskContribution(
            Long portfolioId, BigDecimal confidenceLevel, Integer horizonDays) {

        RiskCalculationInput input = dataService.loadInput(portfolioId);
        return quantEngineClient.calculateRiskContribution(
                new HistoricalVarRequest(confidenceLevel, horizonDays, input.quantPositions()));
    }

    public StressScenarioResponse runStressTest(
            Long portfolioId, String scenarioName,
            BigDecimal equityShockPct, BigDecimal rateShockBps, BigDecimal fxShockPct) {

        RiskCalculationInput input = dataService.loadInput(portfolioId);
        StressScenarioRequest request = new StressScenarioRequest(
                scenarioName, input.baseCurrency(), equityShockPct, rateShockBps, fxShockPct,
                BigDecimal.valueOf(5.0), BigDecimal.valueOf(40.0), // default bond duration/convexity
                input.stressPositions());

        return quantEngineClient.runStressTest(request);
    }

    public HistoricalVarCalculationResponse getCalculation(Long id) {
        return toResponse(dataService.findCalculation(id));
    }

    private HistoricalVarCalculationResponse save(
            Long portfolioId, RiskCalculationInput input, String methodology,
            BigDecimal confidenceLevel, Integer horizonDays, BigDecimal portfolioValue,
            BigDecimal varAmount, BigDecimal expectedShortfall, Integer observationCount,
            Object requestForAudit) {

        RiskCalculation calculation = new RiskCalculation();
        calculation.setPortfolioId(portfolioId);
        calculation.setMethodology(methodology);
        calculation.setConfidenceLevel(confidenceLevel);
        calculation.setHorizonDays(horizonDays);
        calculation.setMarketDataAsOf(input.marketDataAsOf());
        calculation.setPortfolioValue(portfolioValue);
        calculation.setVarAmount(varAmount);
        calculation.setExpectedShortfall(expectedShortfall);
        calculation.setObservationCount(observationCount);
        calculation.setStatus("COMPLETED");
        calculation.setRequestParams(toAuditJson(requestForAudit));

        return toResponse(dataService.saveCalculation(calculation));
    }

    private HistoricalVarCalculationResponse toResponse(RiskCalculation c) {
        return new HistoricalVarCalculationResponse(
                c.getId(), c.getPortfolioId(), c.getMethodology(),
                c.getConfidenceLevel(), c.getHorizonDays(), c.getCalculationTimestamp(),
                c.getMarketDataAsOf(), c.getPortfolioValue(), c.getVarAmount(),
                c.getExpectedShortfall(), c.getObservationCount(), c.getStatus());
    }

    private String toAuditJson(Object request) {
        try {
            return objectMapper.writeValueAsString(request);
        } catch (JsonProcessingException e) {
            // Keep the result, but never lose the audit payload without a trace.
            logger.error("Could not serialise risk calculation request for the audit trail", e);
            return null;
        }
    }
}
