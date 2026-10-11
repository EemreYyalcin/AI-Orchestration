package dev.orchestrationlab.incident.reasoning.application;
import dev.orchestrationlab.incident.context.application.EvidenceBundle;
/**
 * Soru ve temizlenmiş kanıtı tipli bulgulara dönüştüren sağlayıcıdan bağımsız sözleşmedir. Bu aşama
 * araç yürütmez; atıf doğrulaması ve fallback SynthesisService sorumluluğundadır.
 */
public interface IncidentSynthesisModel {
    /**
     * Eldeki EvidenceBundle üzerinden özet, olası nedenler, tavsiyeler, atıflar ve eylem önerisi
     * üretir.
     * @param question Kullanıcının olay sorusu; kalıcı oluşturma sınırı 4000 karakterdir.
     */
    IncidentSynthesis synthesize(String question, EvidenceBundle evidence);
}
