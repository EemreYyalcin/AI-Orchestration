package dev.orchestrationlab.incident.context.application;

import dev.orchestrationlab.incident.tool.application.ToolKind;

/**
 * Tek bir temizlenmiş kanıt öğesidir: id rapordaki atıf anahtarı, source ToolKind kaynağı, summary
 * sınırlı metin özetidir. Kimlik yalnızca kaydedildiği paket içinde anlamlıdır; incelemeler arasında
 * küresel kimlik değildir.
 * @param id İşlem yapılan dahili kayıt veya kanıt kimliği.
 * @param source Kanıtın geldiği tipli araç kaynağı.
 * @param summary Boyutu sözleşmeyle sınırlanan açıklama/kanıt metni.
 */
public record Evidence(String id, ToolKind source, String summary) {
    /**
     * Kimliğin null/64 karakterden uzun olmadığını, kaynağın mevcut ve özetin boş olmayıp en fazla
     * 10000 karakter olduğunu doğrular.
     */
    public Evidence {
        if (id == null || id.length() > 64 || source == null || summary == null || summary.isBlank()
                || summary.length() > 10000) throw new IllegalArgumentException("Invalid evidence");
    }
}
