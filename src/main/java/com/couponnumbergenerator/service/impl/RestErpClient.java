package com.couponnumbergenerator.service.impl;

import com.couponnumbergenerator.config.ErpProperties;
import com.couponnumbergenerator.dto.request.ErpRedemptionRequest;
import com.couponnumbergenerator.dto.response.ErpRedemptionResponse;
import com.couponnumbergenerator.exception.ErpIntegrationException;
import com.couponnumbergenerator.service.ErpClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Slf4j
@Service
@ConditionalOnProperty(prefix = "app.erp", name = "mock", havingValue = "false", matchIfMissing = true)
public class RestErpClient implements ErpClient {

    private final RestClient restClient;
    private final ErpProperties properties;

    public RestErpClient(@Qualifier("erpRestClient") RestClient restClient, ErpProperties properties) {
        this.restClient = restClient;
        this.properties = properties;
    }

    @Override
    public String postRedemption(ErpRedemptionRequest request) {
        ErpRedemptionResponse response;
        try {
            response = restClient.post()
                    .uri(properties.redemptionEndpoint())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(ErpRedemptionResponse.class);
        } catch (RestClientException ex) {
            throw new ErpIntegrationException(
                    "ERP redemption call failed for request %d: %s".formatted(request.referenceId(), ex.getMessage()), ex);
        }
        if (response == null || response.documentNumber() == null || response.documentNumber().isBlank()) {
            throw new ErpIntegrationException(
                    "ERP accepted redemption request %d but returned no document number".formatted(request.referenceId()));
        }
        log.info("ERP posted redemption request {} as document {}", request.referenceId(), response.documentNumber());
        return response.documentNumber();
    }
}