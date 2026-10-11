package dev.orchestrationlab.incident.reasoning.infrastructure;
import java.nio.charset.StandardCharsets;
import org.springframework.core.io.ClassPathResource;
/**
 * Yalnızca paketlenmiş v1 sistem prompt’larını UTF-8 olarak yükleyen katalogdur. İzinli adlar
 * classification, synthesis ve tool-investigation’dır; kullanıcı/model girdisi rastgele dosya yoluna
 * dönüşmez.
 */
public final class PromptCatalog {
    public static final String VERSION = "v1";
    /**
     * Yalnızca statik yardımcı metotlarla kullanılan bu sınıfın örneklenmesini engeller.
     */
    private PromptCatalog() { }
    /**
     * İzinli prompt adını doğrulayıp classpath içindeki prompts/&lt;ad&gt;-v1.txt dosyasını okur.
     * Bilinmeyen ad veya eksik kaynakta açık hata üretir.
     */
    public static String load(String name) {
        if (!java.util.Set.of("classification", "synthesis", "tool-investigation").contains(name)) throw new IllegalArgumentException("Unknown prompt");
        try { return new ClassPathResource("prompts/" + name + "-v1.txt").getContentAsString(StandardCharsets.UTF_8); }
        catch (java.io.IOException missing) { throw new IllegalStateException("Prompt unavailable", missing); }
    }
}
