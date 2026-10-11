package dev.orchestrationlab.incident.context.application;

import java.util.List;
import dev.orchestrationlab.incident.tool.application.*;

/**
 * Toplanan kanıtları, kaynak hatalarını ve bütçe nedeniyle kırpılma bilgisini birlikte taşır. Başarılı
 * kaynaklar eksik kaynaklarla birlikte raporlanabilir; koleksiyonların sonradan dışarıdan
 * değiştirilmesi engellenir.
 * @param items Kimlikli ve temizlenmiş kanıt öğeleri.
 * @param failures Kaynaklara ait güvenli hata kayıtları.
 * @param truncated Bütçe nedeniyle kanıt metni/öğe seçimi kırpıldıysa true.
 */
public record EvidenceBundle(List<Evidence> items, List<SourceFailure> failures, boolean truncated) {
    /**
     * Kanıt ve kaynak hata listelerini değişmez kopyalar; kırpılma bilgisini aynen taşır.
     */
    public EvidenceBundle { items = List.copyOf(items); failures = List.copyOf(failures); }
    /**
     * Tek kaynağın güvenli hata türünü bildirir; ham exception veya sağlayıcı hata metni taşımaz.
     * @param source Kanıtın geldiği tipli araç kaynağı.
     */
    public record SourceFailure(ToolKind source, ToolResult.Failure failure) { }
    /**
     * Paket kırpılmışsa veya herhangi bir kaynak hatası varsa true döndürür. Bu bayrak kanıtın
     * eksiksizliğini anlatır; çıkarımların doğruluğunu kanıtlamaz.
     */
    public boolean degraded() { return truncated || !failures.isEmpty(); }
}
