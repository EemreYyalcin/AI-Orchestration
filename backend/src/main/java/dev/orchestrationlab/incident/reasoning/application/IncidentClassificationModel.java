package dev.orchestrationlab.incident.reasoning.application;
/**
 * Soru metnini tipli olay sınıflandırmasına dönüştüren sağlayıcıdan bağımsız model sınırıdır.
 * Uygulamaları canlı Spring AI adaptörü ve deterministik çevrimdışı modeldir.
 */
public interface IncidentClassificationModel {
    /**
     * Soru için IncidentClassification üretir; çıktının araç yetkisi ve fallback politikası
     * ClassificationService tarafından ayrıca denetlenir.
     * @param question Kullanıcının olay sorusu; kalıcı oluşturma sınırı 4000 karakterdir.
     */
    IncidentClassification classify(String question); }
