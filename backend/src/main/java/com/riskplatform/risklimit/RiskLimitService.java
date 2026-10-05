package com.riskplatform.risklimit;

import com.riskplatform.portfolio.Position;
import com.riskplatform.portfolio.PositionRepository;
import com.riskplatform.risk.RiskCalculation;
import com.riskplatform.risk.RiskCalculationRepository;
import com.riskplatform.risklimit.dto.RiskLimitDtos.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class RiskLimitService {

    private final RiskLimitRepository riskLimitRepository;
    private final PositionRepository positionRepository;
    private final RiskCalculationRepository riskCalculationRepository;

    public RiskLimitService(RiskLimitRepository riskLimitRepository,
                             PositionRepository positionRepository,
                             RiskCalculationRepository riskCalculationRepository) {
        this.riskLimitRepository = riskLimitRepository;
        this.positionRepository = positionRepository;
        this.riskCalculationRepository = riskCalculationRepository;
    }

    public RiskLimitResponse createLimit(Long portfolioId, CreateRiskLimitRequest request) {
        RiskLimit limit = new RiskLimit();
        limit.setPortfolioId(portfolioId);
        limit.setLimitType(RiskLimit.LimitType.valueOf(request.limitType()));
        limit.setLimitValue(request.limitValue());
        if (request.warningThresholdPct() != null) {
            limit.setWarningThresholdPct(request.warningThresholdPct());
        }
        limit = riskLimitRepository.save(limit);
        return toResponse(limit);
    }

    @Transactional(readOnly = true)
    public List<RiskLimitResponse> listLimits(Long portfolioId) {
        return riskLimitRepository.findByPortfolioId(portfolioId).stream()
                .map(this::toResponse)
                .toList();
    }

    public void deleteLimit(Long limitId) {
        riskLimitRepository.deleteById(limitId);
    }

    @Transactional(readOnly = true)
    public PortfolioLimitEvaluationResponse evaluateLimits(Long portfolioId) {
        List<RiskLimit> limits = riskLimitRepository.findByPortfolioId(portfolioId);
        List<Position> positions = positionRepository.findByPortfolioId(portfolioId);

        // Only fetched if actually needed, since not every limit type needs it
        Optional<RiskCalculation> latestCalculation = findLatestCalculation(portfolioId);

        List<LimitEvaluation> evaluations = limits.stream()
                .map(limit -> evaluateOne(limit, positions, latestCalculation))
                .toList();

        return new PortfolioLimitEvaluationResponse(portfolioId, evaluations);
    }

    private LimitEvaluation evaluateOne(RiskLimit limit, List<Position> positions,
                                         Optional<RiskCalculation> latestCalculation) {

        BigDecimal currentValue = switch (limit.getLimitType()) {
            case VAR -> latestCalculation
                    .map(RiskCalculation::getVarAmount)
                    .orElseThrow(() -> new IllegalStateException(
                            "No risk calculation on record for portfolio " + limit.getPortfolioId() +
                            "; run a VaR calculation before evaluating a VAR limit"));

            case EXPECTED_SHORTFALL -> latestCalculation
                    .map(RiskCalculation::getExpectedShortfall)
                    .orElseThrow(() -> new IllegalStateException(
                            "No risk calculation on record for portfolio " + limit.getPortfolioId() +
                            "; run a VaR calculation before evaluating an EXPECTED_SHORTFALL limit"));

            case EQUITY_EXPOSURE_PCT -> equityExposurePct(positions);

            case FX_EXPOSURE -> fxExposureValue(positions);
        };

        LimitStatus status = LimitStatus.classify(
                currentValue, limit.getLimitValue(), limit.getWarningThresholdPct());

        BigDecimal utilizationPct = limit.getLimitValue().compareTo(BigDecimal.ZERO) == 0
                ? BigDecimal.ZERO
                : currentValue.abs().divide(limit.getLimitValue().abs(), 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100));

        return new LimitEvaluation(
                limit.getId(), limit.getLimitType().name(), limit.getLimitValue(),
                currentValue, utilizationPct, status.name());
    }

    private BigDecimal equityExposurePct(List<Position> positions) {
        BigDecimal total = positions.stream()
                .map(Position::marketValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (total.compareTo(BigDecimal.ZERO) == 0) return BigDecimal.ZERO;

        BigDecimal equityValue = positions.stream()
                .filter(p -> p.getInstrumentType() == Position.InstrumentType.EQUITY
                        || p.getInstrumentType() == Position.InstrumentType.ETF)
                .map(Position::marketValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return equityValue.divide(total, 6, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100));
    }

    private BigDecimal fxExposureValue(List<Position> positions) {
        return positions.stream()
                .filter(p -> p.getInstrumentType() == Position.InstrumentType.FX)
                .map(Position::marketValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private Optional<RiskCalculation> findLatestCalculation(Long portfolioId) {
        // No dedicated "latest per portfolio" query exists yet on
        // RiskCalculationRepository -- findAll + filter is fine at
        // current data volumes; worth a proper indexed query
        // (findTopByPortfolioIdOrderByCalculationTimestampDesc) if
        // this table grows large.
        return riskCalculationRepository.findAll().stream()
                .filter(c -> c.getPortfolioId().equals(portfolioId))
                .max(Comparator.comparing(RiskCalculation::getCalculationTimestamp));
    }

    private RiskLimitResponse toResponse(RiskLimit limit) {
        return new RiskLimitResponse(
                limit.getId(), limit.getPortfolioId(), limit.getLimitType().name(),
                limit.getLimitValue(), limit.getWarningThresholdPct());
    }
}