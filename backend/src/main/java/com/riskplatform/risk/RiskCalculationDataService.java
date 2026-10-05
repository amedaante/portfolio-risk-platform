package com.riskplatform.risk;

import com.riskplatform.marketdata.MarketPrice;
import com.riskplatform.marketdata.MarketPriceRepository;
import com.riskplatform.portfolio.Position;
import com.riskplatform.portfolio.PositionRepository;
import com.riskplatform.risk.dto.QuantEngineDtos.PositionInput;
import com.riskplatform.risk.dto.QuantEngineDtos.PricePoint;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
public class RiskCalculationDataService {

    private final PositionRepository positionRepository;
    private final MarketPriceRepository marketPriceRepository;

    public RiskCalculationDataService(PositionRepository positionRepository,
                                      MarketPriceRepository marketPriceRepository) {
        this.positionRepository = positionRepository;
        this.marketPriceRepository = marketPriceRepository;
    }

    @Transactional(readOnly = true)
    public RiskCalculationInput loadInput(Long portfolioId) {
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
                    .map(price -> new PricePoint(price.getPriceDate(), price.getPrice()))
                    .toList();
            positionInputs.add(new PositionInput(
                    position.getInstrumentId(),
                    position.getQuantity(),
                    latest.getPrice(),
                    priceHistory));
        }

        return new RiskCalculationInput(marketDataAsOf, List.copyOf(positionInputs));
    }

    public record RiskCalculationInput(LocalDate marketDataAsOf, List<PositionInput> positions) {}
}
