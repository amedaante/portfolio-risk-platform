package com.riskplatform.risk;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.riskplatform.risk.dto.QuantEngineDtos.HistoricalVarRequest;
import com.riskplatform.risk.dto.QuantEngineDtos.HistoricalVarResponse;
import com.riskplatform.risk.dto.QuantEngineDtos.PositionInput;
import com.riskplatform.risk.dto.QuantEngineDtos.PricePoint;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class QuantEngineClientTest {

    private static final String BASE_URL = "http://quant-engine.test";

    @Test
    void sendsHistoricalVarRequestAsIsoDateJson() {
                RestClient.Builder builder = newRestClientBuilder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        QuantEngineClient client = new QuantEngineClient(builder.build());

        server.expect(requestTo(BASE_URL + "/risk/historical-var"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().json("""
                        {
                          "confidenceLevel": 0.95,
                          "horizonDays": 1,
                          "positions": [{
                            "instrumentId": "AAPL",
                            "quantity": 2,
                            "currentPrice": 190,
                            "priceHistory": [{"date": "2026-09-25", "price": 190}]
                          }]
                        }
                        """))
                .andRespond(withSuccess("""
                        {
                          "methodology": "HISTORICAL_SIMULATION",
                          "confidenceLevel": 0.95,
                          "horizonDays": 1,
                          "portfolioValue": 380,
                          "var": 12,
                          "expectedShortfall": 15,
                          "observationCount": 1,
                          "worstLoss": 15,
                          "bestGain": 8
                        }
                        """, MediaType.APPLICATION_JSON));

        HistoricalVarResponse response = client.calculateHistoricalVar(sampleRequest());

        assertEquals(new BigDecimal("12"), response.var());
        server.verify();
    }

    @Test
    void reportsDownstreamStatusWithoutExposingResponseBody() {
                RestClient.Builder builder = newRestClientBuilder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        QuantEngineClient client = new QuantEngineClient(builder.build());

        server.expect(requestTo(BASE_URL + "/risk/historical-var"))
                .andRespond(withStatus(HttpStatus.UNPROCESSABLE_ENTITY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"detail\":\"bad input\"}"));

        QuantEngineException exception = assertThrows(
                QuantEngineException.class,
                () -> client.calculateHistoricalVar(sampleRequest()));

        assertEquals(422, exception.getDownstreamStatus());
        assertEquals("Quant engine returned HTTP 422", exception.getMessage());
        server.verify();
    }

    private HistoricalVarRequest sampleRequest() {
        return new HistoricalVarRequest(
                new BigDecimal("0.95"),
                1,
                List.of(new PositionInput(
                        "AAPL",
                        new BigDecimal("2"),
                        new BigDecimal("190"),
                        List.of(new PricePoint(LocalDate.of(2026, 9, 25), new BigDecimal("190"))))));
    }

        private RestClient.Builder newRestClientBuilder() {
                ObjectMapper objectMapper = JsonMapper.builder()
                                .addModule(new JavaTimeModule())
                                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                                .build();
                RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
                builder.messageConverters(converters -> converters.stream()
                                .filter(MappingJackson2HttpMessageConverter.class::isInstance)
                                .map(MappingJackson2HttpMessageConverter.class::cast)
                                .findFirst()
                                .orElseThrow()
                                .setObjectMapper(objectMapper));
                return builder;
        }
}
