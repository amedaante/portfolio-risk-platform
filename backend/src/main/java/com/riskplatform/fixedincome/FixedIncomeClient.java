package com.riskplatform.fixedincome;

import com.riskplatform.fixedincome.dto.FixedIncomeDtos.*;
import com.riskplatform.risk.QuantEngineException;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Component
public class FixedIncomeClient {

    private final RestClient restClient;

    public FixedIncomeClient(RestClient quantEngineRestClient) {
        this.restClient = quantEngineRestClient;
    }

    public BondAnalyticsResponse analyzeBond(BondParams params) {
        try {
            return restClient.post()
                    .uri("/fixed-income/analyze")
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(params)
                    .retrieve()
                    .body(BondAnalyticsResponse.class);
        } catch (RestClientResponseException e) {
            throw new QuantEngineException(e.getStatusCode().value(), e);
        } catch (RestClientException e) {
            throw new QuantEngineException(null, e);
        }
    }

    public RateShockResponse rateShock(RateShockRequest request) {
        try {
            return restClient.post()
                    .uri("/fixed-income/rate-shock")
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(RateShockResponse.class);
        } catch (RestClientResponseException e) {
            throw new QuantEngineException(e.getStatusCode().value(), e);
        } catch (RestClientException e) {
            throw new QuantEngineException(null, e);
        }
    }
}