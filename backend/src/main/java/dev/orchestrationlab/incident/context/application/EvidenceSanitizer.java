package dev.orchestrationlab.incident.context.application;

import java.util.regex.Pattern;

/**
 * Kanıt metnindeki yaygın sır ve e-posta biçimlerini maskeleyen yardımcıdır. Kontrol karakterlerini de
 * temizler. Örüntü tabanlı ek savunmadır; tüm sırları bulma veya prompt injection’ı tamamen engelleme
 * garantisi vermez.
 */
public final class EvidenceSanitizer {
    private static final Pattern SECRET = Pattern.compile("(?i)(password|api[_-]?key|secret|access[_-]?token|authorization)\\s*[:=]\\s*(?:Bearer\\s+)?[^\\s,;]+" );
    private static final Pattern EMAIL = Pattern.compile("[\\w.+-]+@[\\w.-]+\\.[A-Za-z]{2,}");
    /**
     * Yalnızca statik yardımcı metotlarla kullanılan bu sınıfın örneklenmesini engeller.
     */
    private EvidenceSanitizer() { }
    /**
     * Satır sonu ve sekme dışındaki kontrol karakterlerini kaldırır;
     * parola/anahtar/token/authorization değerlerini, sk- anahtarlarını ve e-postaları maskeler. Sonuç
     * daha sonra kanıt bütçelerine göre kırpılır.
     */
    public static String sanitize(String text) {
        String cleaned = text.replaceAll("[\\p{Cc}&&[^\\n\\t]]", " ");
        cleaned = SECRET.matcher(cleaned).replaceAll("$1=[REDACTED]");
        cleaned = cleaned.replaceAll("\\bsk-[A-Za-z0-9_-]{8,}\\b", "[REDACTED]");
        return EMAIL.matcher(cleaned).replaceAll("[EMAIL]");
    }
}
