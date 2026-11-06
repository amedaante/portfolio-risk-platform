package com.riskplatform.risk;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.InvalidDefinitionException;
import com.riskplatform.marketdata.FxRateService;
import com.riskplatform.marketdata.MarketPrice;
import com.riskplatform.marketdata.MarketPriceRepository;
import com.riskplatform.portfolio.Portfolio;
import com.riskplatform.portfolio.PortfolioRepository;
import com.riskplatform.portfolio.Position;
import com.riskplatform.portfolio.PositionRepository;
import com.riskplatform.risk.dto.QuantEngineDtos.HistoricalVarRequest;
import com.riskplatform.risk.dto.QuantEngineDtos.HistoricalVarResponse;
import com.riskplatform.risk.dto.QuantEngineDtos.PricePoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.*;

/**
 * Proves the transaction boundaries of a risk calculation without a
 * database: a real Spring context applies the real @Transactional proxies,
 * and a no-op transaction manager lets us observe when a transaction is
 * active. Repositories and the quant engine are Mockito mocks whose
 * answers record the transaction state at the moment they are called.
 */
@SpringJUnitConfig(RiskCalculationTransactionBoundaryTest.Config.class)
class RiskCalculationTransactionBoundaryTest {

    @Autowired RiskCalculationService service;
    @Autowired SelfInvocationDemo selfInvocationDemo;
    @Autowired PortfolioRepository portfolioRepository;
    @Autowired PositionRepository positionRepository;
    @Autowired MarketPriceRepository marketPriceRepository;
    @Autowired RiskCalculationRepository riskCalculationRepository;
    @Autowired QuantEngineClient quantEngineClient;

    private final AtomicReference<TxState> duringLoad = new AtomicReference<>();
    private final AtomicReference<TxState> duringEngineCall = new AtomicReference<>();
    private final AtomicReference<TxState> duringSave = new AtomicReference<>();

    @BeforeEach
    void stubCollaborators() {
        reset(portfolioRepository, positionRepository, marketPriceRepository,
                riskCalculationRepository, quantEngineClient);

        Portfolio portfolio = new Portfolio();
        portfolio.setId(7L);
        portfolio.setBaseCurrency("USD");
        when(portfolioRepository.findById(7L)).thenAnswer(inv -> {
            duringLoad.set(TxState.now());
            return Optional.of(portfolio);
        });
        when(positionRepository.findByPortfolioId(7L)).thenReturn(List.of(position("AAPL", "2")));
        when(marketPriceRepository.findByInstrumentIdInOrderByInstrumentIdAscPriceDateAsc(anyCollection()))
                .thenReturn(List.of(price("AAPL", "2026-01-01", "100"), price("AAPL", "2026-01-02", "101")));

        when(quantEngineClient.calculateHistoricalVar(any())).thenAnswer(inv -> {
            duringEngineCall.set(TxState.now());
            return new HistoricalVarResponse("HISTORICAL_SIMULATION", new BigDecimal("0.95"), 1,
                    new BigDecimal("202"), new BigDecimal("3"), new BigDecimal("4"), 1,
                    new BigDecimal("4"), new BigDecimal("2"));
        });

        when(riskCalculationRepository.save(any())).thenAnswer(inv -> {
            duringSave.set(TxState.now());
            RiskCalculation saved = inv.getArgument(0);
            saved.setId(99L);
            return saved;
        });
    }

    @Test
    void quantEngineCallRunsOutsideAnyTransaction() {
        service.calculateHistoricalVar(7L, new BigDecimal("0.95"), 1);

        assertEquals(new TxState(true, true), duringLoad.get(), "inputs load in a read-only transaction");
        assertEquals(new TxState(false, false), duringEngineCall.get(), "no transaction during the network call");
        assertEquals(new TxState(true, false), duringSave.get(), "result saves in a read-write transaction");
    }

    @Test
    void auditTrailStoresTheFullRequestIncludingDates() throws Exception {
        service.calculateHistoricalVar(7L, new BigDecimal("0.95"), 1);

        ArgumentCaptor<RiskCalculation> saved = ArgumentCaptor.forClass(RiskCalculation.class);
        verify(riskCalculationRepository).save(saved.capture());
        String requestParams = saved.getValue().getRequestParams();

        assertNotNull(requestParams, "request_params must not be silently dropped");
        HistoricalVarRequest replayed = new ObjectMapper().findAndRegisterModules()
                .readValue(requestParams, HistoricalVarRequest.class);
        assertEquals(LocalDate.of(2026, 1, 2), replayed.positions().get(0).priceHistory().get(1).date());
    }

    @Test
    void rootCauseOfTheOldAuditBug_plainObjectMapperCannotWriteLocalDate() {
        ObjectMapper handBuilt = new ObjectMapper(); // what the service used to create itself
        assertThrows(InvalidDefinitionException.class,
                () -> handBuilt.writeValueAsString(new PricePoint(LocalDate.of(2026, 1, 2), BigDecimal.ONE)));
    }

    @Test
    void selfInvocationBypassesTheTransactionalProxy() {
        // Same @Transactional method, two ways of calling it.
        assertTrue(selfInvocationDemo.transactional(), "called through the proxy: transaction active");
        assertFalse(selfInvocationDemo.callsItself(), "called via `this`: annotation silently ignored");
    }

    // ---------------------------------------------------------------------

    record TxState(boolean active, boolean readOnly) {
        static TxState now() {
            return new TxState(TransactionSynchronizationManager.isActualTransactionActive(),
                    TransactionSynchronizationManager.isCurrentTransactionReadOnly());
        }
    }

    static class SelfInvocationDemo {
        @Transactional
        public boolean transactional() {
            return TransactionSynchronizationManager.isActualTransactionActive();
        }

        public boolean callsItself() {
            return this.transactional(); // `this` is the raw object, not the proxy
        }
    }

    /** Begins/commits nothing, but lets Spring track transaction state normally. */
    static class RecordingTransactionManager extends AbstractPlatformTransactionManager {
        @Override protected Object doGetTransaction() { return new Object(); }
        @Override protected void doBegin(Object transaction, TransactionDefinition definition) {}
        @Override protected void doCommit(DefaultTransactionStatus status) {}
        @Override protected void doRollback(DefaultTransactionStatus status) {}
    }

    @Configuration
    @EnableTransactionManagement
    static class Config {
        @Bean PlatformTransactionManager transactionManager() { return new RecordingTransactionManager(); }
        @Bean PortfolioRepository portfolioRepository() { return mock(PortfolioRepository.class); }
        @Bean PositionRepository positionRepository() { return mock(PositionRepository.class); }
        @Bean MarketPriceRepository marketPriceRepository() { return mock(MarketPriceRepository.class); }
        @Bean RiskCalculationRepository riskCalculationRepository() { return mock(RiskCalculationRepository.class); }
        @Bean QuantEngineClient quantEngineClient() { return mock(QuantEngineClient.class); }
        @Bean ObjectMapper objectMapper() { return Jackson2ObjectMapperBuilder.json().build(); } // as Boot configures it
        @Bean FxRateService fxRateService(MarketPriceRepository prices) { return new FxRateService(prices); }
        @Bean SelfInvocationDemo selfInvocationDemo() { return new SelfInvocationDemo(); }

        @Bean
        RiskCalculationDataService riskCalculationDataService(PortfolioRepository portfolios, PositionRepository positions,
                                                              MarketPriceRepository prices, RiskCalculationRepository calculations,
                                                              FxRateService fx) {
            return new RiskCalculationDataService(portfolios, positions, prices, calculations, fx);
        }

        @Bean
        RiskCalculationService riskCalculationService(RiskCalculationDataService dataService,
                                                      QuantEngineClient client, ObjectMapper objectMapper) {
            return new RiskCalculationService(dataService, client, objectMapper);
        }
    }

    private static Position position(String instrumentId, String quantity) {
        Position position = new Position();
        position.setInstrumentId(instrumentId);
        position.setInstrumentType(Position.InstrumentType.EQUITY);
        position.setQuantity(new BigDecimal(quantity));
        position.setPrice(new BigDecimal("100"));
        position.setCurrency("USD");
        position.setValuationDate(LocalDate.of(2026, 1, 2));
        return position;
    }

    private static MarketPrice price(String instrumentId, String date, String value) {
        MarketPrice price = new MarketPrice();
        price.setInstrumentId(instrumentId);
        price.setPriceDate(LocalDate.parse(date));
        price.setPrice(new BigDecimal(value));
        price.setCurrency("USD");
        return price;
    }
}
