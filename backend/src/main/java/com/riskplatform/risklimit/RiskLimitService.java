package com.riskplatform.risklimit;

import com.riskplatform.common.ApiException;
import com.riskplatform.marketdata.FxConverter;
import com.riskplatform.marketdata.FxRateService;
import com.riskplatform.portfolio.Portfolio;
import com.riskplatform.portfolio.PortfolioNotFoundException;
import com.riskplatform.portfolio.PortfolioRepository;
import com.riskplatform.portfolio.Position;
import com.riskplatform.portfolio.PositionRepository;
import com.riskplatform.risk.RiskCalculation;
import com.riskplatform.risk.RiskCalculationRepository;
import com.riskplatform.risklimit.dto.RiskLimitDtos.*;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

@Service
@Transactional
public class RiskLimitService {

    private final RiskLimitRepository riskLimitRepository;
    private final PortfolioRepository portfolioRepository;
    private final PositionRepository positionRepository;
    private final RiskCalculationRepository riskCalculationRepository;
    private final FxRateService fxRateService;

    public RiskLimitService(RiskLimitRepository riskLimitRepository,
                            PortfolioRepository portfolioRepository,
                            PositionRepository positionRepository,
                            RiskCalculationRepository riskCalculationRepository,
                            FxRateService fxRateService) {
        this.riskLimitRepository = riskLimitRepository;
        this.portfolioRepository = portfolioRepository;
        this.positionRepository = positionRepository;
        this.riskCalculationRepository = riskCalculationRepository;
        this.fxRateService = fxRateService;
    }

    public RiskLimitResponse createLimit(Long portfolioId, CreateRiskLimitRequest request) {
        if (!portfolioRepository.existsById(portfolioId)) {
            throw new PortfolioNotFoundException(portfolioId);
        }
        RiskLimit limit = new RiskLimit();
        limit.setPortfolioId(portfolioId);
        limit.setLimitType(RiskLimit.LimitType.valueOf(request.limitType()));
        limit.setLimitValue(request.limitValue());
        if (request.warningThresholdPct() != null) {
            limit.setWarningThresholdPct(request.warningThresholdPct());
        }
        limit.setMethodology(request.methodology());
        limit.setConfidenceLevel(request.confidenceLevel());
        limit.setHorizonDays(request.horizonDays());
        limit = riskLimitRepository.save(limit);
        return toResponse(limit);
    }

    @Transactional(readOnly = true)
    public List<RiskLimitResponse> listLimits(Long portfolioId) {
        return riskLimitRepository.findByPortfolioId(portfolioId).stream()
                .map(this::toResponse)
                .toList();
    }

    public void deleteLimit(Long portfolioId, Long limitId) {
        // Scope by portfolio so one portfolio's URL can't delete another's limit.
        RiskLimit limit = riskLimitRepository.findByIdAndPortfolioId(limitId, portfolioId)
                .orElseThrow(() -> ApiException.notFound(
                        "Risk limit " + limitId + " not found in portfolio " + portfolioId));
        riskLimitRepository.delete(limit);
    }

    @Transactional(readOnly = true)
    public PortfolioLimitEvaluationResponse evaluateLimits(Long portfolioId) {
        Portfolio portfolio = portfolioRepository.findById(portfolioId)
                .orElseThrow(() -> new PortfolioNotFoundException(portfolioId));
        List<RiskLimit> limits = riskLimitRepository.findByPortfolioId(portfolioId);
        List<Position> positions = positionRepository.findByPortfolioId(portfolioId);
        FxConverter fx = fxRateService.converterTo(portfolio.getBaseCurrency());

        List<LimitEvaluation> evaluations = limits.stream()
                .map(limit -> evaluateOne(limit, positions, fx))
                .toList();

        return new PortfolioLimitEvaluationResponse(portfolioId, evaluations);
    }

    private LimitEvaluation evaluateOne(RiskLimit limit, List<Position> positions, FxConverter fx) {
        return switch (limit.getLimitType()) {
            case VAR -> evaluateAgainstCalculation(limit, RiskCalculation::getVarAmount);
            case EXPECTED_SHORTFALL -> evaluateAgainstCalculation(limit, RiskCalculation::getExpectedShortfall);
            case EQUITY_EXPOSURE_PCT -> classified(limit, equityExposurePct(positions, fx),
                    "current positions, % of portfolio value in " + fx.baseCurrency());
            case FX_EXPOSURE -> classified(limit, fxExposureValue(positions, fx),
                    "current FX positions, in " + fx.baseCurrency());
        };
    }

    private LimitEvaluation evaluateAgainstCalculation(RiskLimit limit, Function<RiskCalculation, BigDecimal> metric) {
        Optional<RiskCalculation> calculation = findLatestMatchingCalculation(limit);
        if (calculation.isEmpty()) {
            // Report this one limit as unknown instead of failing the whole evaluation.
            return new LimitEvaluation(limit.getId(), limit.getLimitType().name(), limit.getLimitValue(),
                    null, null, LimitStatus.NO_DATA.name(), "no matching risk calculation; " + describeScope(limit));
        }
        RiskCalculation c = calculation.get();
        return classified(limit, metric.apply(c), "calculation " + c.getId() + ": " + c.getMethodology()
                + ", " + c.getConfidenceLevel().stripTrailingZeros().toPlainString()
                + " confidence, " + c.getHorizonDays() + "-day, as of " + c.getMarketDataAsOf());
    }

    private LimitEvaluation classified(RiskLimit limit, BigDecimal currentValue, String basis) {
        LimitStatus status = LimitStatus.classify(
                currentValue, limit.getLimitValue(), limit.getWarningThresholdPct());

        BigDecimal utilizationPct = limit.getLimitValue().compareTo(BigDecimal.ZERO) == 0
                ? BigDecimal.ZERO
                : currentValue.abs().divide(limit.getLimitValue().abs(), 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100));

        return new LimitEvaluation(
                limit.getId(), limit.getLimitType().name(), limit.getLimitValue(),
                currentValue, utilizationPct, status.name(), basis);
    }

    /**
     * Latest calculation for the limit's portfolio that matches whatever
     * scope the limit sets. Previously this was findAll() + filter in
     * memory, and it ignored methodology/confidence/horizon entirely.
     */
    private Optional<RiskCalculation> findLatestMatchingCalculation(RiskLimit limit) {
        Specification<RiskCalculation> matchesScope = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("portfolioId"), limit.getPortfolioId()));
            if (limit.getMethodology() != null) {
                predicates.add(cb.equal(root.get("methodology"), limit.getMethodology()));
            }
            if (limit.getConfidenceLevel() != null) {
                predicates.add(cb.equal(root.get("confidenceLevel"), limit.getConfidenceLevel()));
            }
            if (limit.getHorizonDays() != null) {
                predicates.add(cb.equal(root.get("horizonDays"), limit.getHorizonDays()));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        Sort newestFirst = Sort.by(Sort.Order.desc("calculationTimestamp"), Sort.Order.desc("id"));
        return riskCalculationRepository.findAll(matchesScope, PageRequest.of(0, 1, newestFirst))
                .stream().findFirst();
    }

    private static String describeScope(RiskLimit limit) {
        return "methodology=" + (limit.getMethodology() == null ? "any" : limit.getMethodology())
                + ", confidence=" + (limit.getConfidenceLevel() == null ? "any" : limit.getConfidenceLevel().toPlainString())
                + ", horizonDays=" + (limit.getHorizonDays() == null ? "any" : limit.getHorizonDays());
    }

    private BigDecimal equityExposurePct(List<Position> positions, FxConverter fx) {
        BigDecimal total = positions.stream()
                .map(p -> valueInBase(p, fx))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (total.compareTo(BigDecimal.ZERO) == 0) return BigDecimal.ZERO;

        BigDecimal equityValue = positions.stream()
                .filter(p -> p.getInstrumentType() == Position.InstrumentType.EQUITY
                        || p.getInstrumentType() == Position.InstrumentType.ETF)
                .map(p -> valueInBase(p, fx))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return equityValue.divide(total, 6, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100));
    }

    private BigDecimal fxExposureValue(List<Position> positions, FxConverter fx) {
        return positions.stream()
                .filter(p -> p.getInstrumentType() == Position.InstrumentType.FX)
                .map(p -> valueInBase(p, fx))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static BigDecimal valueInBase(Position position, FxConverter fx) {
        return fx.toBase(position.marketValue(), position.priceCurrency(), position.getValuationDate());
    }

    private RiskLimitResponse toResponse(RiskLimit limit) {
        return new RiskLimitResponse(
                limit.getId(), limit.getPortfolioId(), limit.getLimitType().name(),
                limit.getLimitValue(), limit.getWarningThresholdPct(),
                limit.getMethodology(), limit.getConfidenceLevel(), limit.getHorizonDays());
    }
}
