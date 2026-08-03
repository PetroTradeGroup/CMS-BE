package com.couponnumbergenerator.ai.impl;

import com.couponnumbergenerator.ai.ExtractedFilter;
import com.couponnumbergenerator.ai.NaturalLanguageQueryPromptBuilder;
import com.couponnumbergenerator.ai.QueryFilterExtractor;
import com.couponnumbergenerator.ai.OllamaProperties;
import com.couponnumbergenerator.ai.client.OllamaChatRequest;
import com.couponnumbergenerator.ai.client.OllamaChatResponse;
import com.couponnumbergenerator.dto.response.FuelTypeResponse;
import com.couponnumbergenerator.enums.CouponStatus;
import com.couponnumbergenerator.exception.AiServiceException;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.LocalDate;
import java.util.List;

@Slf4j
@Service
public class OllamaQueryFilterExtractor implements QueryFilterExtractor {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private final RestClient restClient;
    private final OllamaProperties properties;
    private final NaturalLanguageQueryPromptBuilder promptBuilder;

    public OllamaQueryFilterExtractor(@Qualifier("ollamaRestClient") RestClient restClient,
                                      OllamaProperties properties,
                                      NaturalLanguageQueryPromptBuilder promptBuilder) {
        this.restClient = restClient;
        this.properties = properties;
        this.promptBuilder = promptBuilder;
    }

    @Override
    public ExtractedFilter extract(String question, List<FuelTypeResponse> availableFuelTypes) {
        log.debug("Extracting filter from question: '{}'", question);

        String prompt = promptBuilder.build(question, availableFuelTypes);
        String rawJson = callOllama(prompt);
        return parseFilter(rawJson, availableFuelTypes);
    }

    private String callOllama(String prompt) {
        OllamaChatRequest request = OllamaChatRequest.json(
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
                throw new AiServiceException("Ollama returned an empty response for query extraction");
            }

            return response.message().content();

        } catch (AiServiceException ex) {
            throw ex;
        } catch (RestClientException ex) {
            log.error("HTTP error calling Ollama for query extraction: {}", ex.getMessage());
            throw new AiServiceException(
                    "Could not reach Ollama at '%s'. Is it running? (ollama serve)".formatted(properties.baseUrl()), ex);
        } catch (Exception ex) {
            log.error("Unexpected error calling Ollama for query extraction", ex);
            throw new AiServiceException("Unexpected error communicating with Ollama", ex);
        }
    }

    private ExtractedFilter parseFilter(String rawResponse, List<FuelTypeResponse> availableFuelTypes) {
        String json = extractJson(rawResponse);
        log.debug("Parsing filter JSON: {}", json);

        try {
            RawFilter raw = MAPPER.readValue(json, RawFilter.class);

            LocalDate dateFrom      = parseDate(raw.dateFrom());
            LocalDate dateTo        = parseDate(raw.dateTo());
            Long fuelTypeId         = validateFuelTypeId(raw.fuelTypeId(), availableFuelTypes);
            CouponStatus status     = parseStatus(raw.status());
            String interpretation   = raw.interpretation() != null ? raw.interpretation().trim() : "Query executed";

            log.info("Extracted filter — status: {}, fuelTypeId: {}, dateFrom: {}, dateTo: {}",
                    status, fuelTypeId, dateFrom, dateTo);
            return new ExtractedFilter(dateFrom, dateTo, fuelTypeId, status, interpretation);

        } catch (AiServiceException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("Failed to parse Ollama filter response: {}", rawResponse, ex);
            throw new AiServiceException(
                    "AI returned a response that could not be parsed as a filter. Try rephrasing your question.");
        }
    }

    /**
     * Finds the outermost JSON object in the response.
     * Handles cases where the model wraps output in ```json ... ``` markdown fences.
     */
    private String extractJson(String raw) {
        String trimmed = raw.strip();
        int start = trimmed.indexOf('{');
        int end   = trimmed.lastIndexOf('}');
        if (start != -1 && end != -1 && end > start) {
            return trimmed.substring(start, end + 1);
        }
        return trimmed;
    }

    private LocalDate parseDate(String value) {
        if (value == null || value.isBlank() || "null".equalsIgnoreCase(value.strip())) {
            return null;
        }
        try {
            return LocalDate.parse(value.strip());
        } catch (Exception ex) {
            log.warn("Could not parse date '{}', ignoring", value);
            return null;
        }
    }

    private CouponStatus parseStatus(String value) {
        if (value == null || value.isBlank() || "null".equalsIgnoreCase(value.strip())) {
            return null;
        }
        try {
            return CouponStatus.valueOf(value.strip().toUpperCase());
        } catch (IllegalArgumentException ex) {
            log.warn("Unknown status '{}', ignoring", value);
            return null;
        }
    }

    private Long validateFuelTypeId(Long id, List<FuelTypeResponse> available) {
        if (id == null) {
            return null;
        }
        boolean exists = available.stream().anyMatch(ft -> ft.id().equals(id));
        if (!exists) {
            log.warn("AI returned fuelTypeId {} which is not in the available list — ignoring", id);
            return null;
        }
        return id;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record RawFilter(
            String dateFrom,
            String dateTo,
            Long fuelTypeId,
            String status,
            String interpretation
    ) {}
}