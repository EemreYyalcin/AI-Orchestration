package dev.orchestrationlab.documentation;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.support.ToolCallbacks;

/**
 * Backend’den ayrı çalışan Spring Boot MCP runbook sunucusunun giriş noktasıdır. Sadece paketlenmiş
 * iki dokümantasyon kaynağını salt okunur araç olarak sunar; orkestrasyon veya iyileştirme yürütmez.
 */
@SpringBootApplication
public class DocumentationServerApplication {
    /**
     * MCP sunucusunun Spring bağlamını ve yapılandırılmış HTTP portunu başlatır.
     */
    public static void main(String[] args) { SpringApplication.run(DocumentationServerApplication.class, args); }
    /**
     * RunbookTools üzerindeki @Tool metotlarını resmi Spring AI callback sağlayıcısına kaydeder;
     * sunucunun sunduğu yetenek searchDocumentation’dır.
     */
    @Bean ToolCallbackProvider documentationTools() { return ToolCallbackProvider.from(ToolCallbacks.from(new RunbookTools())); }
}
