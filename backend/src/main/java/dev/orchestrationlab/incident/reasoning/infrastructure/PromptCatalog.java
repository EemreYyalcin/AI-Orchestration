package dev.orchestrationlab.incident.reasoning.infrastructure;
import java.nio.charset.StandardCharsets;
import org.springframework.core.io.ClassPathResource;
public final class PromptCatalog {
    public static final String VERSION = "v1";
    private PromptCatalog() { }
    public static String load(String name) {
        if (!java.util.Set.of("classification", "synthesis", "tool-investigation").contains(name)) throw new IllegalArgumentException("Unknown prompt");
        try { return new ClassPathResource("prompts/" + name + "-v1.txt").getContentAsString(StandardCharsets.UTF_8); }
        catch (java.io.IOException missing) { throw new IllegalStateException("Prompt unavailable", missing); }
    }
}
