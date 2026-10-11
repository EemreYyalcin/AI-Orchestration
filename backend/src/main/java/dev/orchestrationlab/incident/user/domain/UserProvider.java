package dev.orchestrationlab.incident.user.domain;

/**
 * Kalıcı kullanıcı kimliğinin desteklenen sağlayıcılarını tanımlar. Mevcut tek değer GOOGLE’dır;
 * subject ancak bu sağlayıcıyla birlikte benzersiz kimlik oluşturur.
 */
public enum UserProvider {
    GOOGLE
}
