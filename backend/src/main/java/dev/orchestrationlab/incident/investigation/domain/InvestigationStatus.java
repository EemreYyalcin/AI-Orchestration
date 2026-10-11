package dev.orchestrationlab.incident.investigation.domain;

/**
 * İnceleme yaşam döngüsü: CREATED kayıt, ANALYZING sınıflandırma, COLLECTING_EVIDENCE toplama,
 * REASONING sentez, WAITING_APPROVAL sahip kararı, COMPLETED başarıyla sonlanma, FAILED yürütme
 * hatası. COMPLETED ret/süre dolmasıyla da oluşabilir; eylemin onaylandığı anlamına gelmez.
 */
public enum InvestigationStatus {
    CREATED, ANALYZING, COLLECTING_EVIDENCE, REASONING, WAITING_APPROVAL, COMPLETED, FAILED
}
