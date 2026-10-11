package dev.orchestrationlab.incident.investigation.domain;
/**
 * Onay alanının izinli değerleridir: NONE onay gerekmiyor, PENDING sahip kararı bekleniyor, APPROVED
 * onay, DENIED ret, TIMED_OUT kalıcı bekleme süresi doldu. İncelemenin genel çalışma durumu
 * InvestigationStatus içinde ayrı tutulur.
 */
public enum ApprovalDecision { NONE, PENDING, APPROVED, DENIED, TIMED_OUT }
