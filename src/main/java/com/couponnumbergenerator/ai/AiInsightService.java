package com.couponnumbergenerator.ai;

/**
 * Abstraction over the AI provider. Swap Ollama for any other LLM by providing
 * a different implementation — nothing else in the codebase changes.
 */
public interface AiInsightService {

    String generateInsight(String prompt);
}