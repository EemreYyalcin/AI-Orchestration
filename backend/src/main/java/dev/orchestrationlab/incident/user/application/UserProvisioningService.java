package dev.orchestrationlab.incident.user.application;

import java.util.Objects;

import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import dev.orchestrationlab.incident.user.domain.AppUser;
import dev.orchestrationlab.incident.user.repository.AppUserRepository;

/**
 * Güvenilir provider/subject kimliğini dahili AppUser kaydına dönüştürür. E-posta kimlik anahtarı
 * değildir. Her deneme REQUIRES_NEW transaction kullanır; yalnızca aynı dış kimliğin eşzamanlı ekleme
 * çakışmasında bir kez tekrar eder.
 */
@Service
public class UserProvisioningService {

    private final AppUserRepository users;
    private final TransactionTemplate transaction;

    /**
     * Repository ve transaction yöneticisini bağlar; her kayıt denemesini REQUIRES_NEW olarak ayarlar.
     * PostgreSQL kısıt hatasından kurtulma yeni transaction gerektirir.
     */
    public UserProvisioningService(AppUserRepository users, PlatformTransactionManager transactionManager) {
        this.users = users;
        this.transaction = new TransactionTemplate(transactionManager);
        // Her deneme çağıranın transaction’ından bağımsızdır. PostgreSQL kısıt hatası
        // transaction’ı iptal eder; yeniden deneme için yeni transaction gerekir.
        this.transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /**
     * Zaten güvenilir kimliği ve profil anlık görüntüsünü kaydeder; OIDC token doğrulamaz. Sadece
     * uk_app_users_provider_subject/23505 çakışmasını rollback sonrasında bir kez tekrarlar; diğer
     * veya ikinci hatayı çağırana iletir. Null profil değerleri eski alanları temizler.
     */
    public AppUser provision(ExternalIdentity identity) {
        Objects.requireNonNull(identity, "identity must not be null");
        try {
            return executeAttempt(identity);
        } catch (DataIntegrityViolationException failure) {
            if (!isIdentityConflict(failure)) {
                throw failure;
            }
            // İlk transaction geri alınmıştır. Tek tekrar genellikle eşzamanlı eklemeyi
            // kazanan satırı okur; ikinci hata doğrudan çağırana aktarılır.
            return executeAttempt(identity);
        }
    }

    /**
     * Yeni bağımsız transaction içinde tek kayıt denemesini çalıştırır; başarısız PostgreSQL
     * transaction’ı yeniden kullanılmaz.
     */
    private AppUser executeAttempt(ExternalIdentity identity) {
        return Objects.requireNonNull(transaction.execute(status -> provisionInTransaction(identity)));
    }

    /**
     * Provider/subject kaydı varsa profilini güncelleyip flush eder; yoksa yeni dahili UUID’li
     * kullanıcı ekler. Flush, yaşam döngüsü callback ve kısıt hatalarını bu transaction içinde görünür
     * kılar.
     */
    private AppUser provisionInTransaction(ExternalIdentity identity) {
        return users.findByProviderAndProviderSubject(identity.provider(), identity.providerSubject())
                .map(existing -> {
                    existing.updateProfile(identity.email(), identity.displayName(), identity.avatarUrl());
                    // Callback ve kısıt denetimi bu transaction içinde gerçekleşsin diye flush edilir.
                    users.flush();
                    return existing;
                })
                .orElseGet(() -> users.saveAndFlush(new AppUser(identity.provider(), identity.providerSubject(),
                        identity.email(), identity.displayName(), identity.avatarUrl())));
    }

    /**
     * Exception neden zincirinde SQLSTATE 23505 ve tam kimlik benzersizlik kısıtı adını birlikte arar.
     * Başka bütünlük hataları yeniden denenmez.
     */
    private boolean isIdentityConflict(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException violation
                    && "23505".equals(violation.getSQLState())
                    && "uk_app_users_provider_subject".equals(violation.getConstraintName())) {
                return true;
            }
        }
        return false;
    }
}
