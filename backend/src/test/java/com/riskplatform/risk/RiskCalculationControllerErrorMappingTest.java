package com.riskplatform.risk;

import com.riskplatform.common.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Web layer only: parameter validation and how failures map to HTTP statuses. */
@WebMvcTest(RiskCalculationController.class)
class RiskCalculationControllerErrorMappingTest {

    @Autowired MockMvc mvc;
    @MockBean RiskCalculationService service;

    @Test
    void confidenceLevelOfOneIsRejectedBeforeReachingTheEngine() throws Exception {
        mvc.perform(post("/api/portfolios/1/risk-calculations/parametric-var").param("confidenceLevel", "1.0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("confidenceLevel")));
        verifyNoInteractions(service);
    }

    @Test
    void zeroHorizonIsRejected() throws Exception {
        mvc.perform(post("/api/portfolios/1/risk-calculations/historical-var").param("horizonDays", "0"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void engineRejectingTheInputIsUnprocessableNotAnOutage() throws Exception {
        when(service.calculateHistoricalVar(anyLong(), any(), any()))
                .thenThrow(new QuantEngineException(422, new RuntimeException("not enough history")));

        mvc.perform(post("/api/portfolios/1/risk-calculations/historical-var"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void engineUnreachableIsBadGateway() throws Exception {
        when(service.calculateHistoricalVar(anyLong(), any(), any()))
                .thenThrow(new QuantEngineException(null, new RuntimeException("connection refused")));

        mvc.perform(post("/api/portfolios/1/risk-calculations/historical-var"))
                .andExpect(status().isBadGateway());
    }

    @Test
    void missingCalculationIsNotFound() throws Exception {
        when(service.getCalculation(5L)).thenThrow(ApiException.notFound("Risk calculation not found: 5"));

        mvc.perform(get("/api/risk-calculations/5"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Risk calculation not found: 5"));
    }
}
