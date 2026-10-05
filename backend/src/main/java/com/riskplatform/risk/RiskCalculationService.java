package com.riskplatform.risk;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.riskplatform.marketdata.MarketPrice;
import com.riskplatform.marketdata.MarketPriceRepository;
import com.riskplatform.portfolio.Position;
import com.riskplatform.portfolio.PositionRepository;
import com.riskplatform.risk.dto.QuantEngineDtos.*;
import com.riskplatform.risk.dto.RiskCalculationDtos.HistoricalVarCalculationResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
public class RiskCalculationService {

    private final PositionRepository positionRepository;
    private final MarketPriceRepository marketPriceRepository;
    private final QuantEngineClient quantEngineClient;
    private final RiskCalculationRepository riskCalculationRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public RiskCalculationService(PositionRepository positionRepository,
                                   MarketPriceRepository marketPriceRepository,
                                   QuantEngineClient quantEngineClient,
                                   RiskCalculationRepository riskCalculationRepository) {
        this.positionRepository = positionRepository;
        this.marketPriceRepository = marketPriceRepository;
        this.quantEngineClient = quantEngineClient;
        this.riskCalculationRepository = riskCalculationRepository;
    }

    // ---- Public entry points: no @Transactional here on purpose. ----
    // Each delegates to a short read transaction, then an untransacted
    // network call, then a short write transaction. No DB connection
    // is held open while we're waiting on the quant engine.

    public HistoricalVarCalculationResponse calculateHistoricalVar(
            Long portfolioId, BigDecimal confidenceLevel, Integer horizonDays) {

        QuantInputs inputs = gatherQuantInputs(portfolioId, confidenceLevel, horizonDays);
        HistoricalVarResponse result = quantEngineClient.calculateHistoricalVar(inputs.request());

        return persistCalculation(
                portfolioId, result.methodology(), confidenceLevel, horizonDays,
                inputs.marketDataAsOf(), result.portfolioValue(), result.var(),
                result.expectedShortfall(), result.observationCount(), inputs.request());
    }

    public HistoricalVarCalculationResponse calculateParametricVar(
            Long portfolioId, BigDecimal confidenceLevel, Integer horizonDays) {

        QuantInputs inputs = gatherQuantInputs(portfolioId, confidenceLevel, horizonDays);
        ParametricVarResponse result = quantEngineClient.calculateParametricVar(inputs.request());

        return persistCalculation(
                portfolioId, result.methodology(), confidenceLevel, horizonDays,
                inputs.marketDataAsOf(), result.portfolioValue(), result.var(),
                result.expectedShortfall(), result.observationCount(), inputs.request());
    }

    public HistoricalVarCalculationResponse calculateMonteCarloVar(
            Long portfolioId, BigDecimal confidenceLevel, Integer horizonDays,
            Integer numSimulations, Long randomSeed) {

        QuantInputs inputs = gatherQuantInputs(portfolioId, confidenceLevel, horizonDays);

        MonteCarloVarRequest mcRequest = new MonteCarloVarRequest(
                confidenceLevel, horizonDays, numSimulations, randomSeed,
                inputs.request().positions());

        MonteCarloVarResponse result = quantEngineClient.calculateMonteCarloVar(mcRequest);

        return persistCalculation(
                portfolioId, result.methodology(), confidenceLevel, horizonDays,
                inputs.marketDataAsOf(), result.portfolioValue(), result.var(),
                result.expectedShortfall(), result.observationCount(), mcRequest);
    }

    public RiskContributionResponse calculateRiskContribution(
            Long portfolioId, BigDecimal confidenceLevel, Integer horizonDays) {

        QuantInputs inputs = gatherQuantInputs(portfolioId, confidenceLevel, horizonDays);
        return quantEngineClient.calculateRiskContribution(inputs.request());
    }

    public StressScenarioResponse runStressTest(
            Long portfolioId, String scenarioName,
            BigDecimal equityShockPct, BigDecimal rateShockBps, BigDecimal fxShockPct) {

        List<Position> positions = positionRepository.findByPortfolioId(portfolioId);
        if (positions.isEmpty()) {
            throw new IllegalStateException("Portfolio has no positions: " + portfolioId);
        }

        List<StressPositionInput> stressInputs = new ArrayList<>();
        for (Position position : positions) {
            List<MarketPrice> history = marketPriceRepository
                    .findByInstrumentIdOrderByPriceDateAsc(position.getInstrumentId());
            if (history.isEmpty()) {
                throw new IllegalStateException(
                        "No market data for instrument: " + position.getInstrumentId());
            }
            BigDecimal latestPrice = history.get(history.size() - 1).getPrice();

            stressInputs.add(new StressPositionInput(
                    position.getInstrumentId(),
                    position.getInstrumentType().name(),
                    position.getQuantity(),
                    latestPrice,
                    null, // no stored bond duration/convexity per position yet; uses scenario defaults
                    null
            ));
        }

        StressScenarioRequest request = new StressScenarioRequest(
                scenarioName, equityShockPct, rateShockBps, fxShockPct,
                BigDecimal.valueOf(5.0), BigDecimal.valueOf(40.0), // default bond duration/convexity
                stressInputs
        );

        return quantEngineClient.runStressTest(request);
    }

    @Transactional(readOnly = true)
    public HistoricalVarCalculationResponse getCalculation(Long id) {
        RiskCalculation calculation = riskCalculationRepository.findById(id)
                .orElseThrow(() -> new IllegalStateException("Risk calculation not found: " + id));
        return toResponse(calculation);
    }

    // ---- Step 1: gather inputs (short read-only transaction) ----

    @Transactional(readOnly = true)
    protected QuantInputs gatherQuantInputs(Long portfolioId, BigDecimal confidenceLevel, Integer horizonDays) {
        List<Position> positions = positionRepository.findByPortfolioId(portfolioId);
        if (positions.isEmpty()) {
            throw new IllegalStateException("Portfolio has no positions: " + portfolioId);
        }

        LocalDate marketDataAsOf = LocalDate.MIN;
        List<PositionInput> positionInputs = new ArrayList<>();

        for (Position position : positions) {
            List<MarketPrice> history = marketPriceRepository
                    .findByInstrumentIdOrderByPriceDateAsc(position.getInstrumentId());

            if (history.isEmpty()) {
                throw new IllegalStateException(
                        "No market data for instrument: " + position.getInstrumentId());
            }

            MarketPrice latest = history.get(history.size() - 1);
            if (latest.getPriceDate().isAfter(marketDataAsOf)) {
                marketDataAsOf = latest.getPriceDate();
            }

            List<PricePoint> priceHistory = history.stream()
                    .map(mp -> new PricePoint(mp.getPriceDate(), mp.getPrice()))
                    .toList();

            positionInputs.add(new PositionInput(
                    position.getInstrumentId(),
                    position.getQuantity(),
                    latest.getPrice(),
                    priceHistory
            ));
        }

        HistoricalVarRequest request = new HistoricalVarRequest(confidenceLevel, horizonDays, positionInputs);
        return new QuantInputs(request, marketDataAsOf);
    }

    // ---- Step 3: persist result (short write transaction) ----

    @Transactional
    protected HistoricalVarCalculationResponse persistCalculation(
        Long portfolioId, String methodology, BigDecimal confidenceLevel, Integer horizonDays,
        LocalDate marketDataAsOf, BigDecimal portfolioValue, BigDecimal varAmount,
        BigDecimal expectedShortfall, Integer observationCount, Object requestForAudit) {

        RiskCalculation calculation = new RiskCalculation();
        calculation.setPortfolioId(portfolioId);
        calculation.setMethodology(methodology);
        calculation.setConfidenceLevel(confidenceLevel);
        calculation.setHorizonDays(horizonDays);
        calculation.setMarketDataAsOf(marketDataAsOf);
        calculation.setPortfolioValue(portfolioValue);
        calculation.setVarAmount(varAmount);
        calculation.setExpectedShortfall(expectedShortfall);
        calculation.setObservationCount(observationCount);
        calculation.setStatus("COMPLETED");
        calculation.setRequestParams(toJsonSafely(requestForAudit));

        calculation = riskCalculationRepository.save(calculation);
        return toResponse(calculation);
    }

    private HistoricalVarCalculationResponse toResponse(RiskCalculation c) {
        return new HistoricalVarCalculationResponse(
                c.getId(), c.getPortfolioId(), c.getMethodology(),
                c.getConfidenceLevel(), c.getHorizonDays(), c.getCalculationTimestamp(),
                c.getMarketDataAsOf(), c.getPortfolioValue(), c.getVarAmount(),
                c.getExpectedShortfall(), c.getObservationCount(), c.getStatus());
    }

    private String toJsonSafely(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return null;
        }
    }

    private record QuantInputs(HistoricalVarRequest request, LocalDate marketDataAsOf) {}
}