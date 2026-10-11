package dev.orchestrationlab.incident.tool.application;

import java.util.Set;
import java.util.UUID;

/**
 * Araç yürütmesinin güvenilir yetki bağlamıdır: ownerId dahili kullanıcı, investigationId sahipli
 * inceleme, allowedTools Java politikasının izinli araçlarıdır. Model argümanlarından oluşturulmaz;
 * UUID’ler zorunlu, izin kümesi değişmez kopyadır.
 * @param ownerId İncelemenin veya oturumun dahili kullanıcı UUID’si.
 * @param investigationId Sahipliği kontrol edilen incelemenin UUID’si.
 * @param allowedTools Java politikasının izin verdiği değişmez araç kümesi.
 */
public record ToolContext(UUID ownerId, UUID investigationId, Set<ToolKind> allowedTools) {
    /**
     * Sahip ve inceleme UUID’lerini zorunlu tutar; Java izin kümesini değişmez kopyalar.
     */
    public ToolContext {
        java.util.Objects.requireNonNull(ownerId);
        java.util.Objects.requireNonNull(investigationId);
        allowedTools = Set.copyOf(allowedTools);
    }
}
