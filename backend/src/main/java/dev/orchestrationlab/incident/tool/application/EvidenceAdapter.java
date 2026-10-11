package dev.orchestrationlab.incident.tool.application;

import java.util.List;

/**
 * Bir kanıt kaynağının salt okunur erişim sözleşmesidir. Adaptör kaynağın nasıl okunduğunu bilir;
 * sahiplik, izin listesi, süre ve çıktı bütçesini ToolExecutionService uygular.
 */
public interface EvidenceAdapter {
    /**
     * Adaptörün sağladığı ToolKind değerini bildirir; yürütme servisi adaptörleri bu anahtarla eşler.
     */
    ToolKind kind();
    /**
     * Doğrulanmış tipli girdiyle ham özet metinlerini okur. Exception çağıran servis tarafından
     * güvenli ToolResult hatasına çevrilir; metinler ayrıca temizlenir ve sınırlandırılır.
     */
    List<String> read(ToolInput input) throws Exception;
}
