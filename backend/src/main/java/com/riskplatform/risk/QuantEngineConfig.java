package com.riskplatform.risk;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

@Configuration
public class QuantEngineConfig {

    @Bean
    public HttpClient quantEngineHttpClient() {
        return HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

        @Bean
        public RestClient quantEngineRestClient(
            RestClient.Builder builder,
            HttpClient quantEngineHttpClient,
            @Value("${quant-engine.base-url}") String baseUrl) {
        JdkClientHttpRequestFactory requestFactory =
            new JdkClientHttpRequestFactory(quantEngineHttpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(60));

        return builder
            .baseUrl(baseUrl)
            .requestFactory(requestFactory)
            .build();
        }
}