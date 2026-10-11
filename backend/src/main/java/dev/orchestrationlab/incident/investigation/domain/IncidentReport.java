package dev.orchestrationlab.incident.investigation.domain;
import java.util.UUID;
import dev.orchestrationlab.incident.reasoning.application.*;
import dev.orchestrationlab.incident.context.application.EvidenceBundle;
/**
 * İnceleme kimliği, sınıflandırma, temizlenmiş kanıt ve tipli bulguları birleştiren kalıcı rapordur.
 * degraded model/kanıt kalitesindeki düşüşü, diagnosticSourcesSimulated operasyonel teşhis
 * kaynaklarının simüle olduğunu bildirir. Rapor onay beklenmeden önce kaydedilir.
 * @param investigationId Sahipliği kontrol edilen incelemenin UUID’si.
 * @param degraded Eksik/kırpılmış kanıt veya yedek model sonucu varsa true.
 * @param diagnosticSourcesSimulated Operasyonel teşhis kaynakları simüle ise true.
 */
public record IncidentReport(UUID investigationId, IncidentClassification classification, EvidenceBundle evidence,
                             IncidentSynthesis findings, boolean degraded, boolean diagnosticSourcesSimulated) { }
