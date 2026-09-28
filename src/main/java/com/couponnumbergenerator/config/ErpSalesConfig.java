package com.couponnumbergenerator.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

@Configuration
@EnableConfigurationProperties(ErpSalesProperties.class)
public class ErpSalesConfig {

    /**
     * A programmatic transaction boundary for {@code CouponSaleServiceImpl.receiveSale}, which
     * retries the whole assignment on transient DB contention (deadlock loser, optimistic-lock
     * failure) — each attempt needs its own fresh transaction, so the boundary sits inside the
     * retry loop rather than on the method (§11.5 of docs/erp-sales-integration-design.md).
     */
    @Bean("saleAssignmentTransactionTemplate")
    public TransactionTemplate saleAssignmentTransactionTemplate(PlatformTransactionManager transactionManager) {
        return new TransactionTemplate(transactionManager);
    }

    @Bean("salesErpRestClient")
    public RestClient salesErpRestClient(ErpSalesProperties properties) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();

        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofSeconds(properties.timeoutSeconds()));

        return RestClient.builder()
                .baseUrl(properties.baseUrl())
                .requestFactory(factory)
                .build();
    }
}