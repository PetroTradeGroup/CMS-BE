package com.couponnumbergenerator.ai.client;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record OllamaChatRequest(
        String model,
        List<Message> messages,
        boolean stream,
        String format
) {
    public record Message(String role, String content) {}


    public static OllamaChatRequest freeText(String model, List<Message> messages) {
        return new OllamaChatRequest(model, messages, false, null);
    }

    public static OllamaChatRequest json(String model, List<Message> messages) {
        return new OllamaChatRequest(model, messages, false, "json");
    }
}