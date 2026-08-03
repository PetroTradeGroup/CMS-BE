package com.couponnumbergenerator.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "app.ai.ollama")
public record OllamaProperties(
        @DefaultValue("http://localhost:11434") String baseUrl,
        @DefaultValue("llama3.2") String model,
        @DefaultValue("60") int timeoutSeconds
) {}