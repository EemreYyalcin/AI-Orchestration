package dev.orchestrationlab.incident.context.application;

import java.util.List;
import java.util.Set;
import dev.orchestrationlab.incident.tool.application.ToolKind;

/**
 * Sıralı seçilmiş kanıt kaynaklarını ve bunlardan zorunlu olanları taşır. sources toplama/birleştirme
 * sırasını, required eksikliği incelemeyi durduracak kaynakları belirtir; koleksiyonlar savunmacı
 * kopyalanır.
 * @param sources Zorunlu önceliğini koruyan sıralı kaynak listesi.
 * @param required Eksikliği yürütmeyi durduracak kaynak kümesi.
 */
public record ContextSelection(List<ToolKind> sources, Set<ToolKind> required) {
    /**
     * Kaynak sırası ve zorunluluk kümesini değişmez kopyalara çevirir; dış koleksiyon değişiklikleri
     * seçimi etkileyemez.
     */
    public ContextSelection { sources = List.copyOf(sources); required = Set.copyOf(required); }
}
