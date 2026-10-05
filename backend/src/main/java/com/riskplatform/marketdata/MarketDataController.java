package com.riskplatform.marketdata;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

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
    public List<MarketPrice> getHistory(
            @PathVariable String instrumentId,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to) {

        if (from != null && to != null) {
            return marketDataService.getPriceHistory(instrumentId, from, to);
        }
        return marketDataService.getPriceHistory(instrumentId);
    }
}