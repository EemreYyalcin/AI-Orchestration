package dev.orchestrationlab.incident.reasoning.application;

import java.util.*;

/**
 * Sentezin tipli sonucu: summary açıklama, likelyCauses olası nedenler, recommendedActions tavsiyeler,
 * evidenceIds kanıt atıfları, proposedAction izinli öneridir. Tavsiye bir yürütme yetkisi değildir;
 * Java hedef politikası ve sahip onayı ayrıca uygulanır.
 * @param summary Boyutu sözleşmeyle sınırlanan açıklama/kanıt metni.
 * @param likelyCauses En fazla beş olası neden; her biri en fazla 500 karakter.
 * @param recommendedActions En fazla beş tavsiye; yürütme yetkisi değildir.
 * @param evidenceIds Sonucun atıf yaptığı paket içi kanıt kimlikleri.
 * @param proposedAction Java politikası ve gerekiyorsa sahip onayı isteyen kapalı eylem önerisi.
 */
public record IncidentSynthesis(String summary, List<String> likelyCauses, List<String> recommendedActions,
                                Set<String> evidenceIds, ProposedAction proposedAction) {
    /**
     * Modelin önerebildiği kapalı eylem kümesidir: NONE eylemsiz sonuç, RESTART_RECOMMENDATION_SERVICE
     * yalnızca recommendation için sahip onaylı mock kayıt. Enum değeri tek başına yürütme yetkisi
     * vermez.
     */
    public enum ProposedAction { NONE, RESTART_RECOMMENDATION_SERVICE }
    /**
     * Özeti 1500 karakterle, neden/tavsiye listelerini beşer öğeyle, atıfları 100 adet/64 karakterle
     * sınırlar. Koleksiyonları değişmez kopyalar ve önerinin null olmamasını ister; atıfların kanıtta
     * bulunması serviste doğrulanır.
     */
    public IncidentSynthesis {
        if (summary == null || summary.isBlank() || summary.length() > 1500) throw new IllegalArgumentException("Invalid summary");
        likelyCauses = bounded(likelyCauses); recommendedActions = bounded(recommendedActions);
        if (evidenceIds == null || evidenceIds.size() > 100 || evidenceIds.stream().anyMatch(id -> id == null || id.length() > 64))
            throw new IllegalArgumentException("Invalid citations");
        evidenceIds = Set.copyOf(evidenceIds); Objects.requireNonNull(proposedAction);
    }
    /**
     * Neden veya tavsiye listesini en fazla beş, boş olmayan ve en fazla 500 karakterlik öğeyle
     * sınırlar; değişmez kopya döndürür.
     */
    private static List<String> bounded(List<String> values) {
        if (values == null || values.size() > 5 || values.stream().anyMatch(s -> s == null || s.isBlank() || s.length() > 500))
            throw new IllegalArgumentException("Invalid conclusions");
        return List.copyOf(values);
    }
}
