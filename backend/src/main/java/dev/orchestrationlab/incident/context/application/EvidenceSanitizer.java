package dev.orchestrationlab.incident.context.application;

import java.util.regex.Pattern;

/** Defense in depth, not a claim of complete secret detection or prompt-injection prevention. */
public final class EvidenceSanitizer {
    private static final Pattern SECRET = Pattern.compile("(?i)(password|api[_-]?key|secret|access[_-]?token|authorization)\\s*[:=]\\s*(?:Bearer\\s+)?[^\\s,;]+" );
    private static final Pattern EMAIL = Pattern.compile("[\\w.+-]+@[\\w.-]+\\.[A-Za-z]{2,}");
    private EvidenceSanitizer() { }
    public static String sanitize(String text) {
        String cleaned = text.replaceAll("[\\p{Cc}&&[^\\n\\t]]", " ");
        cleaned = SECRET.matcher(cleaned).replaceAll("$1=[REDACTED]");
        cleaned = cleaned.replaceAll("\\bsk-[A-Za-z0-9_-]{8,}\\b", "[REDACTED]");
        return EMAIL.matcher(cleaned).replaceAll("[EMAIL]");
    }
}
