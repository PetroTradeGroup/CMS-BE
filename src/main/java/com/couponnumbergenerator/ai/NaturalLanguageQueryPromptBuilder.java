package com.couponnumbergenerator.ai;

import com.couponnumbergenerator.dto.response.FuelTypeResponse;
import com.couponnumbergenerator.enums.CouponStatus;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class NaturalLanguageQueryPromptBuilder {

    private static final String VALID_STATUSES = Arrays.stream(CouponStatus.values())
            .map(Enum::name)
            .collect(Collectors.joining(", "));

    public String build(String question, List<FuelTypeResponse> fuelTypes) {
        return """
                Today's date: %s

                You are a query parser for a fuel coupon management system.
                Extract filter parameters from the user's question and return ONLY a JSON object — no explanation, no markdown, just JSON.

                %s

                Valid coupon statuses: %s

                Return exactly this JSON structure (use null for fields that are not mentioned):
                {
                  "dateFrom": "YYYY-MM-DD or null",
                  "dateTo": "YYYY-MM-DD or null",
                  "fuelTypeId": <number from the list above, or null>,
                  "status": "<one of the valid statuses above, or null>",
                  "interpretation": "<one sentence describing what you understood>"
                }

                User question: "%s"
                """.formatted(LocalDate.now(), formatFuelTypes(fuelTypes), VALID_STATUSES, question);
    }

    private String formatFuelTypes(List<FuelTypeResponse> fuelTypes) {
        if (fuelTypes.isEmpty()) {
            return "Available fuel types: none configured.";
        }
        StringBuilder sb = new StringBuilder("Available fuel types (use the exact ID):\n");
        fuelTypes.forEach(ft ->
                sb.append("  - ID ").append(ft.id())
                        .append(": ").append(ft.name())
                        .append(" (code: ").append(ft.typeCode()).append(")")
                        .append(ft.active() ? "" : " [inactive]")
                        .append("\n")
        );
        return sb.toString().stripTrailing();
    }
}