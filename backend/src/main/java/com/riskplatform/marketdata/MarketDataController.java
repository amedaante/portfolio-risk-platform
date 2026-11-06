package com.riskplatform.marketdata;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/market-data")
public class MarketDataController {

    private final MarketDataService marketDataService;

    public MarketDataController(MarketDataService marketDataService) {
        this.marketDataService = marketDataService;
    }

    @PostMapping(value = "/upload", consumes = "multipart/form-data")
    public ResponseEntity<Map<String, Object>> upload(@RequestParam("file") MultipartFile file) {
        int count = marketDataService.ingestCsv(file);
        return ResponseEntity.ok(Map.of("rowsIngested", count));
    }

    @GetMapping("/{instrumentId}/history")
    public List<PriceResponse> getHistory(
            @PathVariable String instrumentId,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to) {

        List<MarketPrice> history = (from != null && to != null)
                ? marketDataService.getPriceHistory(instrumentId, from, to)
                : marketDataService.getPriceHistory(instrumentId);
        // Return a DTO, not the JPA entity, so the API shape doesn't change
        // whenever the table does.
        return history.stream()
                .map(p -> new PriceResponse(p.getInstrumentId(), p.getPriceDate(), p.getPrice(), p.getCurrency()))
                .toList();
    }

    public record PriceResponse(String instrumentId, LocalDate priceDate, BigDecimal price, String currency) {}
}
