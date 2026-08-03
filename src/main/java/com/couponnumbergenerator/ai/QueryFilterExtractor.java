package com.couponnumbergenerator.ai;

import com.couponnumbergenerator.dto.response.FuelTypeResponse;

import java.util.List;

/**
 * Abstraction over the AI provider used for filter extraction.
 * Swap Ollama for any other LLM by providing a different implementation.
 */
public interface QueryFilterExtractor {

    ExtractedFilter extract(String question, List<FuelTypeResponse> availableFuelTypes);
}