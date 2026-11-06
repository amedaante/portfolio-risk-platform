package com.riskplatform.fixedincome;

import com.riskplatform.fixedincome.dto.FixedIncomeDtos.*;
import com.riskplatform.risk.QuantEngineClient;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class FixedIncomeClient {

    private final RestClient restClient;

    public FixedIncomeClient(RestClient quantEngineRestClient) {
        this.restClient = quantEngineRestClient;
    }

    public BondAnalyticsResponse analyzeBond(BondParams params) {
        return QuantEngineClient.post(restClient, "/fixed-income/analyze", params, BondAnalyticsResponse.class);
    }

    public RateShockResponse rateShock(RateShockRequest request) {
        return QuantEngineClient.post(restClient, "/fixed-income/rate-shock", request, RateShockResponse.class);
    }
}
