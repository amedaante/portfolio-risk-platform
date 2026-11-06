package com.riskplatform.risk;

import com.riskplatform.common.ApiException;
import com.riskplatform.marketdata.FxConverter;
import com.riskplatform.marketdata.FxRateService;
import com.riskplatform.marketdata.MarketPrice;
import com.riskplatform.marketdata.MarketPriceRepository;
import com.riskplatform.portfolio.Portfolio;
import com.riskplatform.portfolio.PortfolioNotFoundException;
import com.riskplatform.portfolio.PortfolioRepository;
import com.riskplatform.portfolio.Position;
import com.riskplatform.portfolio.PositionRepository;
import com.riskplatform.risk.dto.QuantEngineDtos.PositionInput;
import com.riskplatform.risk.dto.QuantEngineDtos.PricePoint;
import com.riskplatform.risk.dto.QuantEngineDtos.StressPositionInput;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.stream.Collectors;

/**
 * All database access for risk calculations, in short transactions.
 *
 * This has to be a separate bean from RiskCalculationService. Spring
 * applies @Transactional through a proxy, and a call from one method to
 * another inside the same object never goes through that proxy. When
 * these methods lived on RiskCalculationService and were called via
 * `this`, their @Transactional annotations silently did nothing.
 */
@Service
public class RiskCalculationDataService {

    private final PortfolioRepository portfolioRepository;
    private final PositionRepository positionRepository;
    private final MarketPriceRepository marketPriceRepository;
    private final RiskCalculationRepository riskCalculationRepository;
    private final FxRateService fxRateService;

    public RiskCalculationDataService(PortfolioRepository portfolioRepository,
                                      PositionRepository positionRepository,
                                      MarketPriceRepository marketPriceRepository,
                                      RiskCalculationRepository riskCalculationRepository,
                                      FxRateService fxRateService) {
        this.portfolioRepository = portfolioRepository;
        this.positionRepository = positionRepository;
        this.marketPriceRepository = marketPriceRepository;
        this.riskCalculationRepository = riskCalculationRepository;
        this.fxRateService = fxRateService;
    }

    /**
     * Loads positions and price histories, valued in the portfolio's base
     * currency. Returns plain records, so nothing lazy escapes the transaction.
     */
    @Transactional(readOnly = true)
    public RiskCalculationInput loadInput(Long portfolioId) {
        Portfolio portfolio = portfolioRepository.findById(portfolioId)
                .orElseThrow(() -> new PortfolioNotFoundException(portfolioId));

        List<Position> positions = positionRepository.findByPortfolioId(portfolioId);
        if (positions.isEmpty()) {
            throw ApiException.unprocessable("Portfolio has no positions: " + portfolioId);
        }

        // One query for all instruments instead of one per position.
        Map<String, List<MarketPrice>> historyByInstrument = marketPriceRepository
                .findByInstrumentIdInOrderByInstrumentIdAscPriceDateAsc(
                        positions.stream().map(Position::getInstrumentId).distinct().toList())
                .stream()
                .collect(Collectors.groupingBy(MarketPrice::getInstrumentId));

        FxConverter fx = fxRateService.converterTo(portfolio.getBaseCurrency());
        LocalDate marketDataAsOf = LocalDate.MIN;
        List<PositionSnapshot> snapshots = new ArrayList<>();

        for (Position position : positions) {
            List<MarketPrice> history = historyByInstrument.getOrDefault(position.getInstrumentId(), List.of());
            if (history.isEmpty()) {
                throw ApiException.unprocessable("No market data for instrument: " + position.getInstrumentId());
            }

            MarketPrice latest = history.get(history.size() - 1);
            if (latest.getPriceDate().isAfter(marketDataAsOf)) {
                marketDataAsOf = latest.getPriceDate();
            }
            snapshots.add(toSnapshot(position, history, latest, fx));
        }

        return new RiskCalculationInput(portfolio.getBaseCurrency(), marketDataAsOf, List.copyOf(snapshots));
    }

    @Transactional
    public RiskCalculation saveCalculation(RiskCalculation calculation) {
        return riskCalculationRepository.save(calculation);
    }

    @Transactional(readOnly = true)
    public RiskCalculation findCalculation(Long id) {
        return riskCalculationRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Risk calculation not found: " + id));
    }

    /**
     * Exposure is quantity x price converted to base currency.
     *
     * The return series depends on the instrument:
     * - FX pair: its own price returns. The pair rate is the risk factor
     *   (P&L of N units of XXXYYY is N x dS in YYY).
     * - Anything else priced in a foreign currency: prices are converted
     *   to base currency date by date, so returns include the FX move.
     * - Priced in base currency: unchanged.
     */
    private PositionSnapshot toSnapshot(Position position, List<MarketPrice> history,
                                        MarketPrice latest, FxConverter fx) {
        String priceCurrency = position.priceCurrency();
        BigDecimal currentPriceBase = fx.toBase(latest.getPrice(), priceCurrency, latest.getPriceDate());

        List<PricePoint> priceHistory;
        if (fx.isBase(priceCurrency) || position.getInstrumentType() == Position.InstrumentType.FX) {
            priceHistory = history.stream()
                    .map(p -> new PricePoint(p.getPriceDate(), p.getPrice()))
                    .toList();
        } else {
            NavigableMap<LocalDate, BigDecimal> rates = fx.rateHistory(priceCurrency);
            priceHistory = new ArrayList<>();
            for (MarketPrice p : history) {
                Map.Entry<LocalDate, BigDecimal> rate = rates.floorEntry(p.getPriceDate());
                if (rate != null) {
                    priceHistory.add(new PricePoint(p.getPriceDate(), p.getPrice().multiply(rate.getValue())));
                }
            }
        }

        return new PositionSnapshot(position.getInstrumentId(), position.getInstrumentType().name(),
                priceCurrency, position.getQuantity(), currentPriceBase, priceHistory);
    }

    public record PositionSnapshot(
            String instrumentId,
            String instrumentType,
            String priceCurrency,
            BigDecimal quantity,
            BigDecimal currentPriceBase,
            List<PricePoint> priceHistory
    ) {}

    public record RiskCalculationInput(String baseCurrency, LocalDate marketDataAsOf, List<PositionSnapshot> positions) {

        public List<PositionInput> quantPositions() {
            return positions.stream()
                    .map(p -> new PositionInput(p.instrumentId(), p.quantity(), p.currentPriceBase(), p.priceHistory()))
                    .toList();
        }

        public List<StressPositionInput> stressPositions() {
            return positions.stream()
                    .map(p -> new StressPositionInput(p.instrumentId(), p.instrumentType(), p.priceCurrency(),
                            p.quantity(), p.currentPriceBase(),
                            null, null)) // no stored bond duration/convexity yet; engine uses scenario defaults
                    .toList();
        }
    }
}
