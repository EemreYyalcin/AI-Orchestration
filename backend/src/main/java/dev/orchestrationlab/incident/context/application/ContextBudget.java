package dev.orchestrationlab.incident.context.application;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("app.context")
public record ContextBudget(@DefaultValue("12") int maxEvidenceItems,
                            @DefaultValue("2000") int maxCharactersPerToolResult,
                            @DefaultValue("8000") int maxTotalCharacters,
                            @DefaultValue("4") int maxTools) {
    public ContextBudget {
        if (maxEvidenceItems < 1 || maxEvidenceItems > 100 || maxCharactersPerToolResult < 1
                || maxCharactersPerToolResult > 10000 || maxTotalCharacters < 1 || maxTotalCharacters > 40000
                || maxTools < 1 || maxTools > 4) throw new IllegalArgumentException("Invalid context budget");
    }
}
