package dev.orchestrationlab.incident.tool.application;

import java.util.List;

/**
 * Araç kaynağını, özet listesini ve varsa güvenli hata türünü taşır. failure=null başarı anlamına
 * gelir; ham exception, sır veya sağlayıcı hata metni dışarı taşınmaz. Liste savunmacı kopyalanır.
 * @param source Kanıtın geldiği tipli araç kaynağı.
 */
public record ToolResult(ToolKind source, List<String> evidence, Failure failure) {
    /**
     * Kanıt listesini değişmez kopyalar; kaynak ve güvenli hata değerini korur.
     */
    public ToolResult { evidence = List.copyOf(evidence); }
    /**
     * Araç hatalarını sınırlı kategorilerde bildirir: FORBIDDEN yetki/sahiplik, INVALID_ARGUMENT
     * girdi, TIMEOUT süre, UNAVAILABLE kaynak erişimi, BUSY dolu kuyruk.
     */
    public enum Failure { FORBIDDEN, INVALID_ARGUMENT, TIMEOUT, UNAVAILABLE, BUSY }
    /**
     * failure null ise çağrıyı başarılı sayar; boş kanıt listesi tek başına hata değildir.
     */
    public boolean successful() { return failure == null; }
    /**
     * Belirtilen kaynak/hata için boş kanıt listeli sonuç üretir; kısmi ham adaptör çıktısı sızdırmaz.
     */
    public static ToolResult failed(ToolKind kind, Failure failure) { return new ToolResult(kind, List.of(), failure); }
}
