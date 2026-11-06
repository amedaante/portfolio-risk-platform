package com.riskplatform.portfolio;

import com.riskplatform.common.ApiException;
import com.riskplatform.marketdata.FxConverter;
import com.riskplatform.marketdata.FxRateService;
import com.riskplatform.portfolio.dto.PortfolioDtos.*;
import com.riskplatform.risk.RiskCalculationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Transactional
public class PortfolioService {

    private final PortfolioRepository portfolioRepository;
    private final PositionRepository positionRepository;
    private final RiskCalculationRepository riskCalculationRepository;
    private final FxRateService fxRateService;

    public PortfolioService(PortfolioRepository portfolioRepository,
                            PositionRepository positionRepository,
                            RiskCalculationRepository riskCalculationRepository,
                            FxRateService fxRateService) {
        this.portfolioRepository = portfolioRepository;
        this.positionRepository = positionRepository;
        this.riskCalculationRepository = riskCalculationRepository;
        this.fxRateService = fxRateService;
    }

    public PortfolioResponse createPortfolio(CreatePortfolioRequest request) {
        Portfolio portfolio = new Portfolio();
        portfolio.setName(request.name());
        portfolio.setBaseCurrency(request.baseCurrency());
        portfolio = portfolioRepository.save(portfolio);
        return toResponse(portfolio, fxRateService.converterTo(portfolio.getBaseCurrency()));
    }

    @Transactional(readOnly = true)
    public PortfolioResponse getPortfolio(Long id) {
        Portfolio portfolio = findPortfolio(id);
        return toResponse(portfolio, fxRateService.converterTo(portfolio.getBaseCurrency()));
    }

    @Transactional(readOnly = true)
    public List<PortfolioResponse> listPortfolios() {
        Map<String, FxConverter> converters = new HashMap<>();
        return portfolioRepository.findAll().stream()
                .map(p -> toResponse(p, converters.computeIfAbsent(p.getBaseCurrency(), fxRateService::converterTo)))
                .toList();
    }

    public PositionResponse addPosition(Long portfolioId, AddPositionRequest request) {
        Portfolio portfolio = findPortfolio(portfolioId);

        Position position = new Position();
        position.setPortfolio(portfolio);
        position.setInstrumentId(request.instrumentId());
        position.setInstrumentType(Position.InstrumentType.valueOf(request.instrumentType()));
        position.setQuantity(request.quantity());
        position.setPrice(request.price());
        position.setCurrency(request.currency());
        position.setValuationDate(request.valuationDate());

        // An FX pair is always quoted in its second currency; reject a
        // contradicting currency instead of silently mis-valuing it.
        if (!position.priceCurrency().equals(request.currency())) {
            throw ApiException.badRequest(request.instrumentId() + " is quoted in "
                    + position.priceCurrency() + ", not " + request.currency());
        }

        position = positionRepository.save(position);
        return toPositionResponse(position, fxRateService.converterTo(portfolio.getBaseCurrency()));
    }

    public void deletePortfolio(Long id) {
        if (!portfolioRepository.existsById(id)) {
            throw new PortfolioNotFoundException(id);
        }
        // risk_calculation is an audit trail, so its FK deliberately has no
        // ON DELETE CASCADE. Refuse clearly instead of failing with a 500.
        if (riskCalculationRepository.existsByPortfolioId(id)) {
            throw ApiException.conflict("Portfolio " + id
                    + " has saved risk calculations and can't be deleted, to preserve the audit trail");
        }
        portfolioRepository.deleteById(id);
    }

    @Transactional(readOnly = true)
    public List<PositionResponse> listPositions(Long portfolioId) {
        FxConverter fx = fxRateService.converterTo(findPortfolio(portfolioId).getBaseCurrency());
        return positionRepository.findByPortfolioId(portfolioId).stream()
                .map(position -> toPositionResponse(position, fx))
                .toList();
    }

    public void removePosition(Long portfolioId, Long positionId) {
        // Scope by portfolio so one portfolio's URL can't delete another's position.
        Position position = positionRepository.findByIdAndPortfolioId(positionId, portfolioId)
                .orElseThrow(() -> ApiException.notFound(
                        "Position " + positionId + " not found in portfolio " + portfolioId));
        position.getPortfolio().getPositions().remove(position);
        positionRepository.delete(position);
    }

    private Portfolio findPortfolio(Long id) {
        return portfolioRepository.findById(id).orElseThrow(() -> new PortfolioNotFoundException(id));
    }

    private PortfolioResponse toResponse(Portfolio portfolio, FxConverter fx) {
        BigDecimal total = portfolio.getPositions().stream()
                .map(position -> valueInBase(position, fx))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new PortfolioResponse(portfolio.getId(), portfolio.getName(),
                portfolio.getBaseCurrency(), total);
    }

    private PositionResponse toPositionResponse(Position position, FxConverter fx) {
        return new PositionResponse(
                position.getId(),
                position.getInstrumentId(),
                position.getInstrumentType().name(),
                position.getQuantity(),
                position.getPrice(),
                position.priceCurrency(),
                position.getValuationDate(),
                position.marketValue(),
                valueInBase(position, fx)
        );
    }

    static BigDecimal valueInBase(Position position, FxConverter fx) {
        return fx.toBase(position.marketValue(), position.priceCurrency(), position.getValuationDate());
    }
}
