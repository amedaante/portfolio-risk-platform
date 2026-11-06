package com.riskplatform.marketdata;

import org.springframework.stereotype.Service;

@Service
public class FxRateService {

    private final MarketPriceRepository marketPriceRepository;

    public FxRateService(MarketPriceRepository marketPriceRepository) {
        this.marketPriceRepository = marketPriceRepository;
    }

    /** Call inside the caller's transaction; the converter queries lazily. */
    public FxConverter converterTo(String baseCurrency) {
        return new FxConverter(baseCurrency, marketPriceRepository::findByInstrumentIdOrderByPriceDateAsc);
    }
}
