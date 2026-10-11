package dev.orchestrationlab.incident.reasoning.application;
/**
 * Modelin bildirdiği önem seviyeleri: LOW düşük, MEDIUM orta, HIGH yüksek, CRITICAL kritik. Mevcut
 * mock restart onayı bu seviyeye göre belirlenmez; sentez ve hedef politikası kullanılır.
 */
public enum Severity { LOW, MEDIUM, HIGH, CRITICAL }
