package com.riskplatform.portfolio;

import com.riskplatform.portfolio.dto.PortfolioDtos.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@Transactional
public class PortfolioService {

    private final PortfolioRepository portfolioRepository;
    private final PositionRepository positionRepository;

    public PortfolioService(PortfolioRepository portfolioRepository,
                             PositionRepository positionRepository) {
        this.portfolioRepository = portfolioRepository;
        this.positionRepository = positionRepository;
    }

    public PortfolioResponse createPortfolio(CreatePortfolioRequest request) {
        Portfolio portfolio = new Portfolio();
        portfolio.setName(request.name());
        portfolio.setBaseCurrency(request.baseCurrency());
        portfolio = portfolioRepository.save(portfolio);
        return toResponse(portfolio);
    }

    @Transactional(readOnly = true)
    public PortfolioResponse getPortfolio(Long id) {
        Portfolio portfolio = portfolioRepository.findById(id)
                .orElseThrow(() -> new PortfolioNotFoundException(id));
        return toResponse(portfolio);
    }

    @Transactional(readOnly = true)
    public List<PortfolioResponse> listPortfolios() {
        return portfolioRepository.findAll().stream()
                .map(this::toResponse)
                .toList();
    }

    public PositionResponse addPosition(Long portfolioId, AddPositionRequest request) {
        Portfolio portfolio = portfolioRepository.findById(portfolioId)
                .orElseThrow(() -> new PortfolioNotFoundException(portfolioId));

        Position position = new Position();
        position.setPortfolio(portfolio);
        position.setInstrumentId(request.instrumentId());
        position.setInstrumentType(Position.InstrumentType.valueOf(request.instrumentType()));
        position.setQuantity(request.quantity());
        position.setPrice(request.price());
        position.setCurrency(request.currency());
        position.setValuationDate(request.valuationDate());

        position = positionRepository.save(position);
        return toPositionResponse(position);
    }

    public void deletePortfolio(Long id) {
        if (!portfolioRepository.existsById(id)) {
            throw new PortfolioNotFoundException(id);
        }
        portfolioRepository.deleteById(id);
    }

    @Transactional(readOnly = true)
    public List<PositionResponse> listPositions(Long portfolioId) {
        return positionRepository.findByPortfolioId(portfolioId).stream()
                .map(this::toPositionResponse)
                .toList();
    }

    public void removePosition(Long positionId) {
        positionRepository.deleteById(positionId);
    }

    private PortfolioResponse toResponse(Portfolio portfolio) {
        BigDecimal total = portfolio.getPositions().stream()
                .map(Position::marketValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new PortfolioResponse(portfolio.getId(), portfolio.getName(),
                portfolio.getBaseCurrency(), total);
    }

    private PositionResponse toPositionResponse(Position position) {
        return new PositionResponse(
                position.getId(),
                position.getInstrumentId(),
                position.getInstrumentType().name(),
                position.getQuantity(),
                position.getPrice(),
                position.getCurrency(),
                position.getValuationDate(),
                position.marketValue()
        );
    }
}