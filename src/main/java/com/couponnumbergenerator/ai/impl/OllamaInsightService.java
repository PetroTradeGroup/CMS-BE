package com.couponnumbergenerator.ai.impl;

import com.couponnumbergenerator.ai.AiInsightService;
import com.couponnumbergenerator.ai.OllamaProperties;
import com.couponnumbergenerator.ai.client.OllamaChatRequest;
import com.couponnumbergenerator.ai.client.OllamaChatResponse;
import com.couponnumbergenerator.exception.AiServiceException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;

@Slf4j
@Service
public class OllamaInsightService implements AiInsightService {

    private final RestClient restClient;
    private final OllamaProperties properties;

    public OllamaInsightService(@Qualifier("ollamaRestClient") RestClient restClient,
                                OllamaProperties properties) {
        this.restClient = restClient;
        this.properties = properties;
    }

    @Override
    public String generateInsight(String prompt) {
        log.debug("Sending insight request to Ollama model '{}'", properties.model());

        OllamaChatRequest request = OllamaChatRequest.freeText(
                properties.model(),
                List.of(new OllamaChatRequest.Message("user", prompt))
        );

        try {
            OllamaChatResponse response = restClient.post()
                    .uri("/api/chat")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(OllamaChatResponse.class);

            if (response == null || response.message() == null || response.message().content() == null) {
                throw new AiServiceException("Ollama returned an empty or malformed response");
            }

            log.debug("Received insight from Ollama ({} chars)", response.message().content().length());
            return response.message().content().trim();

        } catch (AiServiceException ex) {
            throw ex;
        } catch (RestClientException ex) {
            log.error("HTTP error calling Ollama at '{}': {}", properties.baseUrl(), ex.getMessage());
            throw new AiServiceException(
                    "Could not reach Ollama at '%s'. Is it running? (ollama serve)".formatted(properties.baseUrl()), ex);
        } catch (Exception ex) {
            log.error("Unexpected error calling Ollama", ex);
            throw new AiServiceException("Unexpected error communicating with Ollama", ex);
        }
    }
}