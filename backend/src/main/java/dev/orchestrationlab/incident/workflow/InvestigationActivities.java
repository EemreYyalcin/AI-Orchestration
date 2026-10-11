package dev.orchestrationlab.incident.workflow;
import io.temporal.activity.*;
import java.util.UUID;
import dev.orchestrationlab.incident.investigation.domain.ApprovalDecision;
/**
 * Temporal’ın yeniden deneyebildiği dış iş adımlarının tipli sözleşmesidir. Model, araç, ağ ve
 * veritabanı işi bu Activity sınırında kalır; workflow yalnızca deterministik sıra ve beklemeyi
 * yönetir.
 */
@ActivityInterface
public interface InvestigationActivities {
    /**
     * Sahipli incelemeyi sınıflandırır ve kalıcı aşamayı ilerletir; tekrar yürütme tamamlanmış aşamayı
     * atlamalıdır.
     * @param owner Güvenilir dahili kullanıcı UUID’si; istek gövdesi/model argümanı değildir.
     * @param id İşlem yapılan dahili kayıt veya kanıt kimliği.
     */
    void classify(UUID owner, UUID id);
    /**
     * Java route politikasına göre kanıt okur ve kaydeder; zorunlu kaynak hatasını yürütmeye bildirir.
     * @param owner Güvenilir dahili kullanıcı UUID’si; istek gövdesi/model argümanı değildir.
     * @param id İşlem yapılan dahili kayıt veya kanıt kimliği.
     */
    void collect(UUID owner, UUID id);
    /**
     * Kanıttan rapor üretir ve kaydeder; sahip onayı beklenmesi gerekiyorsa true döndürür.
     * @param owner Güvenilir dahili kullanıcı UUID’si; istek gövdesi/model argümanı değildir.
     * @param id İşlem yapılan dahili kayıt veya kanıt kimliği.
     */
    boolean analyze(UUID owner, UUID id);
    /**
     * Onay/ret/süre dolması kararını kalıcı görünüme yazar ve mock eylem/tamamlanma politikasını
     * uygular.
     * @param owner Güvenilir dahili kullanıcı UUID’si; istek gövdesi/model argümanı değildir.
     * @param id İşlem yapılan dahili kayıt veya kanıt kimliği.
     * @param decision Tipli sahip kararı; geçerli değerler aşama/politika tarafından denetlenir.
     */
    void complete(UUID owner, UUID id, ApprovalDecision decision);
    /**
     * Yürütme hatasını PostgreSQL inceleme görünümüne FAILED olarak aktarır; bu yazma da başarısız
     * olursa görünüm geride kalabilir.
     * @param owner Güvenilir dahili kullanıcı UUID’si; istek gövdesi/model argümanı değildir.
     * @param id İşlem yapılan dahili kayıt veya kanıt kimliği.
     */
    void fail(UUID owner, UUID id);
}
