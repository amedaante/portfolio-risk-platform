package com.riskplatform.portfolio;

import com.riskplatform.portfolio.dto.PortfolioDtos.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/portfolios")
public class PortfolioController {

    private final PortfolioService portfolioService;

    public PortfolioController(PortfolioService portfolioService) {
        this.portfolioService = portfolioService;
    }

    @PostMapping
    public ResponseEntity<PortfolioResponse> createPortfolio(@Valid @RequestBody CreatePortfolioRequest request) {
        PortfolioResponse response = portfolioService.createPortfolio(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public List<PortfolioResponse> listPortfolios() {
        return portfolioService.listPortfolios();
    }

    @GetMapping("/{id}")
    public PortfolioResponse getPortfolio(@PathVariable Long id) {
        return portfolioService.getPortfolio(id);
    }

    @PostMapping("/{id}/positions")
    public ResponseEntity<PositionResponse> addPosition(@PathVariable Long id,
                                                          @Valid @RequestBody AddPositionRequest request) {
        PositionResponse response = portfolioService.addPosition(id, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}/positions")
    public List<PositionResponse> listPositions(@PathVariable Long id) {
        return portfolioService.listPositions(id);
    }

    @DeleteMapping("/positions/{positionId}")
    public ResponseEntity<Void> removePosition(@PathVariable Long positionId) {
        portfolioService.removePosition(positionId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletePortfolio(@PathVariable Long id) {
        portfolioService.deletePortfolio(id);
        return ResponseEntity.noContent().build();
    }
}