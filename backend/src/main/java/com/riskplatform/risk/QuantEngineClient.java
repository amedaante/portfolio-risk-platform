package com.riskplatform.risk;

import com.riskplatform.risk.dto.QuantEngineDtos.HistoricalVarRequest;
import com.riskplatform.risk.dto.QuantEngineDtos.HistoricalVarResponse;
import com.riskplatform.risk.dto.QuantEngineDtos.ParametricVarResponse;
import com.riskplatform.risk.dto.QuantEngineDtos.MonteCarloVarRequest;
import com.riskplatform.risk.dto.QuantEngineDtos.MonteCarloVarResponse;
import com.riskplatform.risk.dto.QuantEngineDtos.RiskContributionResponse;
import com.riskplatform.risk.dto.QuantEngineDtos.StressScenarioRequest;
import com.riskplatform.risk.dto.QuantEngineDtos.StressScenarioResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Component
public class QuantEngineClient {

    private final RestClient restClient;

    public QuantEngineClient(RestClient quantEngineRestClient) {
        this.restClient = quantEngineRestClient;
    }

    public HistoricalVarResponse calculateHistoricalVar(HistoricalVarRequest request) {
        try {
            return restClient.post()
                    .uri("/risk/historical-var")
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(HistoricalVarResponse.class);
        } catch (RestClientResponseException e) {
            throw new QuantEngineException(e.getStatusCode().value(), e);
        } catch (RestClientException e) {
            throw new QuantEngineException(null, e);
        }
    }

    public ParametricVarResponse calculateParametricVar(HistoricalVarRequest request) {
        try {
            return restClient.post()
                    .uri("/risk/parametric-var")
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(ParametricVarResponse.class);
        } catch (RestClientResponseException e) {
            throw new QuantEngineException(e.getStatusCode().value(), e);
        } catch (RestClientException e) {
            throw new QuantEngineException(null, e);
        }
    }

    public MonteCarloVarResponse calculateMonteCarloVar(MonteCarloVarRequest request) {
        try {
            return restClient.post()
                    .uri("/risk/monte-carlo-var")
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(MonteCarloVarResponse.class);
        } catch (RestClientResponseException e) {
            throw new QuantEngineException(e.getStatusCode().value(), e);
        } catch (RestClientException e) {
            throw new QuantEngineException(null, e);
        }
    }

    public RiskContributionResponse calculateRiskContribution(HistoricalVarRequest request) {
        try {
            return restClient.post()
                    .uri("/risk/risk-contribution")
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(RiskContributionResponse.class);
        } catch (RestClientResponseException e) {
            throw new QuantEngineException(e.getStatusCode().value(), e);
        } catch (RestClientException e) {
            throw new QuantEngineException(null, e);
        }
    }

    public StressScenarioResponse runStressTest(StressScenarioRequest request) {
        try {
            return restClient.post()
                    .uri("/risk/stress-test")
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(StressScenarioResponse.class);
        } catch (RestClientResponseException e) {
            throw new QuantEngineException(e.getStatusCode().value(), e);
        } catch (RestClientException e) {
            throw new QuantEngineException(null, e);
        }
    }
}   