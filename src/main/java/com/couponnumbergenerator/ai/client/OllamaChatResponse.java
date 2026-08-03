package com.couponnumbergenerator.ai.client;

public record OllamaChatResponse(
        String model,
        Message message,
        boolean done
) {
    public record Message(String role, String content) {}
}