package com.riskplatform.fixedincome;

import com.riskplatform.fixedincome.dto.FixedIncomeDtos.*;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/fixed-income")
public class FixedIncomeController {

    private final FixedIncomeClient fixedIncomeClient;

    public FixedIncomeController(FixedIncomeClient fixedIncomeClient) {
        this.fixedIncomeClient = fixedIncomeClient;
    }

    @PostMapping("/analyze")
    public BondAnalyticsResponse analyze(@Valid @RequestBody BondParams params) {
        return fixedIncomeClient.analyzeBond(params);
    }

    @PostMapping("/rate-shock")
    public RateShockResponse rateShock(@Valid @RequestBody RateShockRequest request) {
        return fixedIncomeClient.rateShock(request);
    }
}