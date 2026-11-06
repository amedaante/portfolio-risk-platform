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
        return post(restClient, "/risk/historical-var", request, HistoricalVarResponse.class);
    }

    public ParametricVarResponse calculateParametricVar(HistoricalVarRequest request) {
        return post(restClient, "/risk/parametric-var", request, ParametricVarResponse.class);
    }

    public MonteCarloVarResponse calculateMonteCarloVar(MonteCarloVarRequest request) {
        return post(restClient, "/risk/monte-carlo-var", request, MonteCarloVarResponse.class);
    }

    public RiskContributionResponse calculateRiskContribution(HistoricalVarRequest request) {
        return post(restClient, "/risk/risk-contribution", request, RiskContributionResponse.class);
    }

    public StressScenarioResponse runStressTest(StressScenarioRequest request) {
        return post(restClient, "/risk/stress-test", request, StressScenarioResponse.class);
    }

    /**
     * One place for the request shape and error translation used by every
     * quant-engine call (also used by FixedIncomeClient).
     */
    public static <T> T post(RestClient restClient, String uri, Object body, Class<T> responseType) {
        try {
            return restClient.post()
                    .uri(uri)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(responseType);
        } catch (RestClientResponseException e) {
            throw new QuantEngineException(e.getStatusCode().value(), e);
        } catch (RestClientException e) {
            throw new QuantEngineException(null, e);
        }
    }
}
