package com.couponnumbergenerator.service.impl;

import com.couponnumbergenerator.config.ErpSalesProperties;
import com.couponnumbergenerator.dto.request.ErpSaleConfirmationRequest;
import com.couponnumbergenerator.exception.ErpIntegrationException;
import com.couponnumbergenerator.service.SaleErpClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Slf4j
@Service
@ConditionalOnProperty(prefix = "app.erp.sales", name = "mock", havingValue = "false", matchIfMissing = true)
public class RestSaleErpClient implements SaleErpClient {

    private final RestClient restClient;
    private final ErpSalesProperties properties;

    public RestSaleErpClient(@Qualifier("salesErpRestClient") RestClient restClient, ErpSalesProperties properties) {
        this.restClient = restClient;
        this.properties = properties;
    }

    @Override
    public void confirmSale(ErpSaleConfirmationRequest request) {
        try {
            restClient.post()
                    .uri(properties.confirmationEndpoint())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException ex) {
            throw new ErpIntegrationException(
                    "BC sale confirmation call failed for document %s: %s"
                            .formatted(request.documentNumber(), ex.getMessage()), ex);
        }
        log.info("BC confirmed sale {} with {} coupon(s)", request.documentNumber(), request.couponNumbers().size());
    }
}