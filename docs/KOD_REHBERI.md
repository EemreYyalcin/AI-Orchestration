# Türkçe kod okuma rehberi

Bu rehber, projeyi ilk kez inceleyen kişinin kaynak dosyalarını hangi sırayla okuyacağını ve kullanılan kavramların ne anlama geldiğini açıklar. Sınıfların, arayüzlerin, record’ların, constructor’ların ve metotların başındaki Türkçe Javadoc açıklamaları ayrıntılı yerel başvuru noktasıdır. React fonksiyonları, Effect blokları ve yapılandırmalar da kendi dosyalarında açıklanmıştır. Güncel sistem sözleşmesi [PROJECT_CONTEXT.md](PROJECT_CONTEXT.md), mimari haritası [ARCHITECTURE.md](ai-orchestration/ARCHITECTURE.md), tarihli kontroller [STATE.md](ai-orchestration/STATE.md) içindedir.

## 1. Proje ne yapıyor?

Kullanıcı bir olay sorusu oluşturur. Uygulama bu soruyu sınıflandırır, Java’nın izin verdiği kaynaklardan sınırlı kanıt toplar ve kaynak kimliklerine atıf yapan rapor üretir. İzinli recommendation restart önerisi varsa rapor kaydedildikten sonra sahibin onayı beklenir. Onay yalnızca benzersiz bir **mock eylem kaydı** oluşturur; gerçek servis yeniden başlatılmaz. Ret veya süre dolması da incelemeyi tamamlar, ancak eylem oluşturmaz.

Varsayılan model deterministik çevrimdışı simülasyondur. Log, veritabanı teşhisi ve metrik kaynakları da simülasyondur. PostgreSQL kalıcılığı, etkin Redis kabul kontrolü ve Temporal/MCP protokolleri gerçek entegrasyonlardır. MCP’nin okuduğu runbook metinleri eğitim için paketlenmiştir. Gerçek protokol kullanımı, canlı üretim teşhisi yapıldığı anlamına gelmez.

## 2. Önce hangi dosyayı okumalıyım?

| Sıra | Kaynak | Okurken cevap aranan soru |
| --- | --- | --- |
| 1 | [App.tsx](../frontend/src/App.tsx), [Investigations.tsx](../frontend/src/Investigations.tsx) | Kullanıcı hangi isteği, hangi oturum/CSRF bilgisiyle gönderiyor? |
| 2 | [InvestigationController.java](../backend/src/main/java/dev/orchestrationlab/incident/investigation/controller/InvestigationController.java) | HTTP isteği hangi kullanım senaryosuna aktarılıyor? |
| 3 | [InvestigationSubmissionService.java](../backend/src/main/java/dev/orchestrationlab/incident/investigation/application/InvestigationSubmissionService.java) | Kabul kontrolü, kayıt ve yürütme hangi sırada? |
| 4 | [InvestigationService.java](../backend/src/main/java/dev/orchestrationlab/incident/investigation/application/InvestigationService.java), [IncidentInvestigation.java](../backend/src/main/java/dev/orchestrationlab/incident/investigation/domain/IncidentInvestigation.java) | Sahiplik, veri ve durum geçişleri nerede korunuyor? |
| 5 | [InvestigationWorkflowImpl.java](../backend/src/main/java/dev/orchestrationlab/incident/workflow/InvestigationWorkflowImpl.java), [InvestigationActivitiesImpl.java](../backend/src/main/java/dev/orchestrationlab/incident/workflow/InvestigationActivitiesImpl.java) | Kalıcı sıra ve dış iş birbirinden nasıl ayrılıyor? |
| 6 | [ClassificationService.java](../backend/src/main/java/dev/orchestrationlab/incident/reasoning/application/ClassificationService.java), [WorkflowRoutingPolicy.java](../backend/src/main/java/dev/orchestrationlab/incident/orchestration/application/WorkflowRoutingPolicy.java) | Model önerisi nasıl Java’nın izinli kaynaklarına dönüşüyor? |
| 7 | [EvidenceCollector.java](../backend/src/main/java/dev/orchestrationlab/incident/orchestration/application/EvidenceCollector.java), [ToolExecutionService.java](../backend/src/main/java/dev/orchestrationlab/incident/tool/application/ToolExecutionService.java) | Paralel okuma, sahiplik, izin ve süre sınırları nerede? |
| 8 | [ContextBuilder.java](../backend/src/main/java/dev/orchestrationlab/incident/context/application/ContextBuilder.java), [EvidenceSanitizer.java](../backend/src/main/java/dev/orchestrationlab/incident/context/application/EvidenceSanitizer.java) | Modele gidecek metin nasıl temizleniyor ve sınırlanıyor? |
| 9 | [SynthesisService.java](../backend/src/main/java/dev/orchestrationlab/incident/orchestration/application/SynthesisService.java) | Atıflar, tekrar ve eylemsiz fallback nasıl doğrulanıyor? |
| 10 | [InvestigationStateService.java](../backend/src/main/java/dev/orchestrationlab/incident/investigation/application/InvestigationStateService.java) | Rapor, karar, denetim ve tek mock eylem nasıl kaydediliyor? |

## 3. Paketler ve temel kavramlar

| Terim / paket | Anlamı ve projedeki karşılığı |
| --- | --- |
| `controller` | HTTP yolu, istek/yanıt ve oturum principal’ı ile ilgilenir. İş kurallarını servislere aktarır. |
| `application` | Kullanım senaryosu ve politika: oluşturma, sınıflandırma, toplama, sentez, durum kaydı. |
| `domain` | Kalıcı iş verisi ve izinli değerler/geçişler: AppUser, IncidentInvestigation ve enum’lar. |
| `repository` | Spring Data JPA üzerinden entity okuma/yazma sınırı. İzinli HTTP erişiminde id ve sahip birlikte aranır. |
| `infrastructure` | OIDC, Spring AI, MCP veya simüle kaynak gibi teknoloji adaptörleri. |
| `configuration` | Spring bean’lerini, profilleri, güvenlik zincirini ve iletişim politikalarını bağlar. |
| `workflow` | Temporal’ın kalıcı sırası, Update/Query sözleşmesi ve Activity uygulamaları. |
| `context` | Seçilmiş, temizlenmiş ve boyutu sınırlandırılmış model kanıtı; uzun süreli bellek değildir. |
| `observability` | Micrometer ölçümleri ve izleme bağlamı; kalıcı denetim tablosundan farklıdır. |
| Constructor | Nesneyi kuran metot. Bağımlılıkları bağlar veya record/entity girdilerini doğrular. |
| Interface | Uygulamanın ihtiyaç duyduğu davranış sözleşmesi. Aynı sözleşme canlı ve çevrimdışı adaptörlerle uygulanabilir. |
| Record / DTO | Alanları birlikte taşıyan tip. API DTO’su entity’nin bütün iç alanlarını otomatik dışarı açmaz. Record koleksiyonları gerektiğinde ayrıca savunmacı kopyalanır. |
| Enum | İzinli değerlerin kapalı kümesi. Model metni rastgele araç veya sınıf adına dönüşmez. |
| Bean | Spring’in oluşturup bağımlılık olarak bağladığı nesne. @Service/@Component/@Bean bu bağlamda kayıt sağlar. |
| Transaction | Birlikte commit veya rollback edilen veritabanı değişiklikleri. Ağ çağrıları transaction boyunca açık tutulmaz. |
| Idempotans | Aynı niyet tekrarlandığında ikinci mantıksal etki oluşmaması. Burada sabit workflow kimliği ve benzersiz mock kayıtlarıyla korunur; gelecekte uzak gerçek eylem için ayrıca protokol gerekir. |
| Fallback / degraded | Kaynak veya model kullanılamayınca temkinli alternatif/kalite düşüşü. Sonuç başarılı görünse bile eksik kanıt gizlenmez. |
| Fan-out / fan-in | Bağımsız kaynak okumalarını paralel başlatma ve sonuçlarını sınırlı tek pakette birleştirme. |

## 4. Bir sorunun yürütme yolu

1. React `App`, `/api/me` ile oturumun durumunu okur. `401` giriş yapılmadığını, ağ/yanıt hatası belirsizliği gösterir. Giriş aynı origin üzerinden Google authorization yoluna gider.
2. `Investigations.mutate`, güncel CSRF bilgisini alır ve soru için POST gönderir. Sahip UUID’si gövdeden alınmaz; backend’in güvenilir principal’ından çıkarılır.
3. `InvestigationSubmissionService.submit`, Redis kabul kontrolünden sonra `InvestigationService.create` çağırır. `temporal` etkinse kayıt ve `start_requested` aynı transaction’da commit edilir.
4. `TemporalInvestigationExecution`, `investigation-<UUID>` kimliğiyle workflow başlatır. Kesintide kayıt kaybolmaz; zamanlanmış dispatcher en fazla 12 deneme yapar. Oluşturma yanıtı kaydedilmiş görünümle `503` olabilir.
5. `InvestigationWorkflowImpl` yalnızca deterministik sırayı yürütür. Model, ağ, normal saat ve veritabanı işleri Activity/servis tarafındadır; kalıcı bekleme `Workflow.await` ile yapılır.
6. Sınıflandırma `IncidentClassification` üretir. Java `WorkflowRoutingPolicy` izinleri denetleyip zorunlu kaynak ve dokümantasyonu ekler.
7. `ContextBuilder.select` zorunlu kaynağı önce seçer. `EvidenceCollector` kaynakları paralel okur. Mevcut hedef **recommendation / ERRORS / limit 4** sabittir; sorudan serbest hedef çıkarımı yoktur.
8. `ToolExecutionService` her okuma öncesi sahiplik/izin/girdi denetler. Ham çıktı maskelenir ve öğe başına 1000 karaktere kırpılır. `ContextBuilder.build` ayrıca öğe/kaynak/toplam bütçelerini uygular.
9. `SynthesisService` bulguların yalnızca eldeki kanıt kimliklerine atıf yapmasını ister. İki başarısız anlamsal deneme sonrası manuel inceleme ve NONE önerili fallback kullanılır.
10. Rapor PostgreSQL’e kaydedilir. Geçerli recommendation restart önerisinde sahip onayı beklenir. Onay, ret veya süre dolması Activity’si kalıcı kararı ve tamamlanmayı yazar. React son duruma kadar sorgular.

`temporal` kapalıysa `IncidentOrchestrator` bu işi açık `/run` çağrısında senkron yürütür. Oluşturma yalnızca CREATED kaydeder; kalıcı onay zamanlayıcısı bulunmaz.

## 5. Güvenlik kodunu anlamak

Google `subject`, sağlayıcı içindeki opaque kimliktir; e-posta değildir. `UserProvisioningService`, `(GOOGLE, subject)` çiftinden kullanıcıyı bulur. `AppUser.id` ayrı dahili UUID’dir. E-postanın değişmesi veya iki subject’in aynı e-postayı paylaşması kullanıcıları birleştirmez.

`GoogleOidcUserService` framework yüklemesinden sonra sınırlı profil alanlarını kayıt servisine aktarır. Kayıt başarısızsa giriş de başarısız olur. `ApplicationOidcUser` protokol verilerini sunucu güvenlik principal’ında temsilciye devreder; API yalnızca `CurrentUser` DTO’sunu döndürür. `DiscardingAuthorizedClientRepository`, access/refresh token’larını giriş sonrası saklamaz.

`SecurityConfiguration` HttpSession ve CSRF kullanır. CSRF token’ı kimlik doğrulama token’ı değildir. JSESSIONID tarayıcı tarafından çerezle gönderilir; JavaScript’in erişim token’ı saklaması gerekmez. Sahiplik filtresinde yabancı kayda `404` dönmesi, kaydın varlığını başka kullanıcıya göstermemek içindir.

Modelin araç önerisi `ToolSession` izin listesini genişletemez. DATABASE girdisinde SQL alanı yoktur; MCP hedefi yönetici yapılandırmasındadır. `@Tool(description=...)` ve `prompts/*.txt` metinleri modelin gördüğü çalışma zamanı politikasının parçasıdır; bunlar geliştirici yorumu değildir.

## 6. Şema ve migration dosyaları

Uygulanmış migration dosyaları tarihsel şema sözleşmesidir. Flyway içerik checksum’ını kaydettiğinden, açıklama amacıyla bile değiştirilmezler. Kaynaklar [db/migration](../backend/src/main/resources/db/migration) altında aynen korunmuştur; aşağıdaki açıklamalar onların okuma rehberidir.

| Migration | Ne ekliyor? | Neden önemli? |
| --- | --- | --- |
| V1 `create_app_users` | Kullanıcı UUID, provider/subject, isteğe bağlı profil ve zamanlar; benzersiz kimlik kısıtı. | Eşzamanlı ilk giriş aynı kimlik için iki kullanıcı oluşturamaz; e-posta benzersiz değildir. |
| V2 `create_investigations` | Sahip FK’si, soru, kapalı durum kümesi, zamanlar ve sahip indeksi. | Kullanıcı kaydı olmadan sahipli inceleme oluşamaz; soru boş olamaz, durum izinli kümede kalır. |
| V3 `persist_investigation_context` | `version`, `classification` ve `evidence` JSONB. | Optimistic locking çakışan yazmaları yakalar; tipli ara sonuçlar kalıcıdır. |
| V4 `investigation_report_and_approval` | JSONB rapor, onay enum kontrolü ve `mock_remediation_actions`. | İnceleme UUID’si mock eylem PK’sidir; aynı inceleme için ikinci kayıt engellenir. |
| V5 `durable_start_requests` | `start_requested`, negatif olamayan `start_attempts`, bekleyen başlatma kısmi indeksi. | Uzak workflow çağrısından önce niyet commit edilir; dispatcher yalnızca bekleyen kayıtları seçebilir. |
| V6 `investigation_audit` | Onay istek zamanı, aşama/sürüm/araç/sonuç denetimi, benzersiz inceleme+aşama+sonuç. | Onay gecikmesi ölçülür; yeniden yürütme aynı denetimi çoğaltmaz. |

`JSONB` PostgreSQL’in yapısal JSON türüdür; classification/evidence/report tipli Java record’ları olarak okunur. `@Version` veritabanı sürüm alanı, `@PrePersist/@PreUpdate` zaman callback’leridir. Hibernate `validate` kullanır; şemayı kendisi değiştirmez.

## 7. Yapılandırma, derleme ve kaynak dosyaları

| Dosya | Görevi |
| --- | --- |
| `backend/pom.xml` | Java/Spring AI/Temporal sürümleri, backend çalışma zamanı ve test bağımlılıkları. BOM sürüm yönetir; dependency ilgili kütüphaneyi ekler. |
| `mcp/documentation-server/pom.xml` | Ayrı MCP uygulamasının bağımlılıkları. İstemci test scope’undadır; sunucu production çalışma zamanı bileşenidir. |
| `application.yml` | Güvenli ortak varsayılanlar, Hibernate/Flyway, oturum, gözlem ve kabul ayarları. |
| `application-local.yml` / `application-container.yml` | Host localhost veya Compose servis adları. Yerel HTTP için Secure çerez false olur; production TLS ayarı ayrıca gereklidir. |
| `application-google.yml` / `application-ai.yml` | İsteğe bağlı dış sağlayıcı giriş/model bağlantısı. Kimlik bilgileri backend ortamından gelir. |
| `application-temporal.yml` | Namespace, bağlantı, worker kuyruğu, workflow/Activity kaydı ve onay süresi. |
| `compose.yaml` / `Dockerfile` dosyaları | Altı yerel servis, sağlık sırası ve çok aşamalı image üretimi. Java image derlemeleri testleri atlar. |
| `.env.example` | Sır içermeyen ortam şablonu. Compose `.env` okur; Maven/IDE için değerler ayrıca yüklenir. |
| `frontend/package.json` | React/Vite/TypeScript sürümleri ve dev/build/preview script’leri. JSON yorum kabul etmediği için açıklama bu rehberdedir. |
| `frontend/tsconfig.json` | TypeScript tip kontrolü/JSX/modül derleme ayarları; build önce `tsc --noEmit` çalıştırır. |
| `frontend/src/vite-env.d.ts` | Vite istemci tiplerine üç eğik çizgili başvuru; çalışma zamanı fonksiyonu değildir. |
| `frontend/index.html` / `main.tsx` / `style.css` | HTML kökü, React bağlama noktası ve temel görünüm kuralları. |
| `frontend/vite.config.ts` / `nginx.conf` | Geliştirme veya container’da aynı-origin API/OIDC/çıkış proxy’si. |
| `prompts/*-v1.txt` | Modele gönderilen sürümlü sistem politikaları; çalışma davranışını korumak için yorum eklenmemiştir. |
| `runbooks/*.txt` | MCP’nin okuduğu paketlenmiş eğitim içerikleri; geliştirici açıklaması eklemek dönen kanıtı değiştirir. |
| `evaluations/incidents.tsv` | Çevrimdışı değerlendirme soru/beklenen tür/kaynak veri seti; satırlar açıklama değil test girdisidir. |
| `mvnw`, `mvnw.cmd`, `.mvn/wrapper/` | Maven’ı sabit sürümle sağlayan upstream wrapper; uygulama iş mantığı değildir ve değiştirilmemiştir. |
| Kilit dosyaları, `target/`, `dist/`, `node_modules/`, `graphify-out/` | Bağımlılık kilidi veya üretilmiş yerel çıktılar; elle Türkçe iş mantığı açıklaması eklenmez. |

## 8. Testleri okurken

Her test metodunun başındaki açıklama tetiklenen durumu ve beklenen sonucu anlatır. MockMvc gerçek HTTP güvenlik zincirini test ederken test principal’ı gerçek Google girişi değildir. Testcontainers PostgreSQL/Redis testleri gerçek izole servis kullanır; Docker gerekir. Temporal TestWorkflowEnvironment gerçek SDK/test sunucusu mekanizmalarını kullanır, production cluster değildir. Sahte ChatModel ile framework şema/callback testi gerçek LLM kalitesini kanıtlamaz.

`*Smoke` sınıfları standart Surefire test seçiminden ayrı, açıkça çalıştırılan yerel kontrollerdir. Özellikle `ContainerStackSmoke` backend container’ını yeniden başlatır ve sentetik satırlar ekler/siler. Sadece yorum eklenen bu çalışmada bu smoke’lar çalıştırılmamıştır. Yeni test sonuçları tarihli [STATE.md](ai-orchestration/STATE.md) kaydında geçmiş doğrulamalardan ayrı tutulur.

## 9. Açıklamaları nasıl sürdürmeliyim?

Davranış değişince sınıf/metot açıklaması da aynı değişiklikte güncellenmelidir. Yorumda olmayan garanti yazmayın: maskeleme bütün sırları bulmaz, doğru kimlikli atıf doğru teşhis demek değildir, mock eylem gerçek restart değildir. Kalıcı workflow ve PostgreSQL görünümü farklı zamanlarda güncellenebilir. Kod/test ile açıklama uyuşmazsa gerçek davranışı doğrulayıp açıklamayı düzeltin.

Türkçe açıklamalar UTF-8 olarak kaydedilmiştir; `Ş`, `Ğ`, `İ`, `ı`, `ö`, `ü`, `ç` harfleri korunur. Dosya görüntülerken editör kodlaması UTF-8 seçilmelidir. Terminalde bozuk görüntü tek başına dosyanın bozuk olduğu anlamına gelmez; kaydedilmiş baytlar ve kaynak derlemesi ayrıca kontrol edilir.
