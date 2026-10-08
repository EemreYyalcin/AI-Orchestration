# AI Incident Orchestrator — yaşayan proje bağlamı

**Güncel mimari için esas başvuru dokümanı.** Depo kaynaklarıyla **2026-10-08** tarihinde karşılaştırılmıştır. Checkpoint 14'e kadar tamamlanan bütünleşik uygulamayı açıklar. Önceki checkpoint dokümanları tarihsel kararları ve doğrulamaları korur; güncel mimariye alternatif şartnameler değildir.

## Bakım Sözleşmesi

**Bu yaşayan bir dokümandır.** Mimariyi, çalışma zamanı bileşenlerini, bağımlılıkları, API'yi, veritabanı şemasını, durum modelini, iş akışını, LLM davranışını, prompt'ları, araçları, MCP'yi, Temporal'ı, güvenliği, Redis'i, gözlemlenebilirliği, ortam yapılandırmasını, testleri veya bilinen kısıtları etkileyen gelecekteki **HER değişiklikte**, bu dosyayı **AYNI değişiklik içinde** güncelleyin.

Çalıştırılabilir kod, yapılandırma, migration dosyaları ve testler doğruluk kaynağı olmaya devam eder. Dokümanla uyuşmazlık varsa: (1) kodu ve testleri inceleyip gerçek davranışı doğrulayın, (2) dokümanı düzeltin, (3) çalışan kodu yalnızca eski dokümana uydurmak için değiştirmeyin. Mevcut uygulama gerçeklerini, önerilen çalışmaları ve geçmiş doğrulamaları birbirinden ayırın. Tarihli test sonuçlarını ancak kontrolleri gerçekten çalıştırdıktan sonra güncelleyin.

Temel kaynak konumlarına aşağıda bağlantı verilmiştir. Aksi belirtilmedikçe Java sınıfları `dev.orchestrationlab.incident` paketindedir. Dokümantasyon güncellemesi tek başına uygulama testlerinin yeniden çalıştırıldığı veya servislerin şu anda sağlıklı olduğu anlamına gelmez; yeni kontroller ayrıca tarihli kaydedilir.

AI orchestration çalışmaları için kısa [mimari haritası](ai-orchestration/ARCHITECTURE.md), tarihli [durum ve doğrulama kaydı](ai-orchestration/STATE.md) ve kullanıcı yol haritası için ayrılmış [eğitim rehberi](ai-orchestration/LEARNING_GUIDE.md) vardır. Bu dosya esas yaşayan bağlam olarak korunur; yeni rehberler bununla tutarlı tutulur. 2026-10-08 envanter çalışmasında uygulama davranışı değiştirilmedi. Bu ayrı çalışmada backend 83 ve MCP 2 test JDK 25.0.2 ile yeniden geçti, frontend tip kontrolü/derlemesi başarılı oldu; altı çalışan Compose servisi healthy, HTTP sağlık ve Temporal cluster kontrolleri başarılıydı. İlk Java 23/wrapper erişim hataları, yeni çalıştırılan kontrollerin sınırı ve çalıştırılmayan canlı-provider/restart smoke'ları STATE.md içinde açıkça kaydedilmiştir. Yukarıdaki dokümantasyon notu ve aşağıdaki tarihsel kayıtlar bu yeni kontrollerin yerine geçmez.

## 1. Projenin Kimliği

**AI Incident Orchestrator**, olay sorularını tipli anlamsal sınıflandırma, Java kontrollü kanıt toplama, kaynak gösteren sentez ve gerektiğinde sahip onayıyla inceler. Kimlik doğrulama, kalıcılık ve küçük React arayüzü bu amacı destekler; ürünün asıl amacı bunlar değildir.

Mevcut olgunluk: gerçek kalıcılık, başlatma kabul kontrolü, MCP iletişimi ve kalıcı iş akışı yürütmesi içeren, üretim ihtiyaçları düşünülerek hazırlanmış bütünleşik yerel uygulama. Operasyonel kaynaklar simülasyon, iyileştirme eylemleri ise mock'tur. Üretim dağıtımı değildir ve canlı modelin teşhis kalitesini kanıtlamaz.

Sabitlenen teknolojiler: Java 25, Spring Boot 4.1.1, Maven Wrapper 3.9.16, Spring MVC/Security/OAuth2 Client/JPA/Flyway/Data Redis/Actuator, PostgreSQL 18, Redis 8, Spring AI 2.0.1, Temporal Java SDK/starter/testing/OpenTracing 1.40.0. Frontend: React/React DOM 19.3.0, TypeScript 7.0.2, Vite 8.3.3, React eklentisi 6.1.2; Node ana sürümü 24, npm ana sürümü 12, paket yöneticisi 12.2.0. Bağımsız derlemeler [backend/pom.xml](../backend/pom.xml), [frontend/package.json](../frontend/package.json) ve [MCP pom.xml](../mcp/documentation-server/pom.xml) dosyalarındadır.

## 2. Temel Mimari İlke

> LLM anlamsal kararlar verir. Deterministik kontrol Java'ya aittir.

| Sorumlu | Görevler | Yetki sınırı |
| --- | --- | --- |
| LLM adaptörü | Olay türünü ve önem seviyesini sınıflandırma, seçilen kanıtı yorumlama, sonuçları ve kaynakları sentezleme, tipli bir eylem önerme; ayrıca ayrı ve sınırlı akıl yürütme gösterimi | Çıktı bir öneridir; şema, yönlendirme, kaynak ve eylem doğrulamasından geçer. Rastgele kod çalıştırma veya iş akışı uygulaması seçme yetkisi yoktur. |
| Java uygulaması | Kimlik doğrulama/sahiplik, transaction'lar, yönlendirme, izinli araç listeleri, girdi/çıktı doğrulama, eşzamanlılık, bütçeler, yeniden denemeler/zaman aşımları, kalıcı durum geçişleri ve onay/eylem politikası | Controller'lar kullanım senaryolarını servislere devreder; güvenilir kimliği ve araç politikasını uygulama kodu sağlar. |
| Temporal | Java'nın tanımladığı kalıcı sırayı yürütme, geçmişi kaydetme, Activity yeniden denemeleri, kalıcı zamanlayıcıyla onay bekleme ve worker yeniden başladığında sürdürme | Olayın anlamına karar vermez, araç izni vermez. LLM iş akışı motoru değildir; Temporal uygulamanın anlamsal orkestrasyon politikasının yerini almaz. |

## 3. Sistemin Güncel Mimarisi

```text
React tarayıcısı :5173 (çerez + CSRF, göreli URL'ler)
  -> Vite proxy (host geliştirme) / nginx (container)
  -> Spring MVC + Spring Security :8080 [backend + Temporal worker süreci]
       -> InvestigationController -> InvestigationSubmissionService
            -> Redis [etkinse atomik oluşturma/çalıştırma kabul kontrolü]
            -> PostgreSQL [sahipli inceleme + başlatma niyetinin commit'i]
            -> TemporalInvestigationExecution [sabit iş akışı kimliği / Update]
                 -> Temporal servisi :7233 [kalıcı geçmiş + onay zamanlayıcısı]
                      -> incident-investigations-v1 görev kuyruğu -> Java worker
                           classify Activity -> model sınıflandırması
                             -> Java yönlendirmesi -> zorunlu kaynaklara öncelik
                           collect Activity -> ToolExecutionService politikası
                             -> paralel log/veritabanı/metrik [simülasyon]
                             -> MCP istemcisi -> özel dokümantasyon sunucusu :8081
                                                -> paketlenmiş runbook'lar
                             -> temizleme/filtreleme/sınırlama -> kanıt birleştirme
                           analyze Activity -> model sentezi -> tip/kaynak doğrulama
                             -> PostgreSQL [rapor; COMPLETED veya WAITING_APPROVAL]
                           kalıcı sahip onayı Update'i / zaman aşımı
                           complete Activity -> karar + benzersiz mock eylem kaydı
                             -> PostgreSQL [COMPLETED, okunabilir rapor]

Flyway -> PostgreSQL şeması V1-V6; Hibernate -> doğrulama/çalışma zamanı eşlemesi
Micrometer + OTel -> HTTP/model/araç/MCP/Temporal gözlemleri
İsteğe bağlı canlı model: Spring AI -> OpenAI (ai profili)
Varsayılan model: etiketlenmiş çevrimdışı test verisi (sağlayıcı kimlik bilgisi yok)
```

Rapor onay beklenmeden **önce** kaydedilir. Durum ve onay dış API görünümündedir; tamamlanma raporu yeniden üretmez. `temporal` olmadan oluşturma yalnızca CREATED kaydeder; açık `/run` çağrısı senkron `IncidentOrchestrator` kullanır ve kalıcı onay zamanlayıcısı içermez.

## 4. Çalışma Zamanı Bileşenleri

| Bileşen / önemli uygulama | Sahip olduğu görevler; bağımlılıklar | Sahip olmadığı görevler |
| --- | --- | --- |
| React `App.tsx`, `Investigations.tsx` | Oturum başlangıcı, sağlık/giriş/çıkış, CSRF alma, oluşturma/durum sorgulama/onay/ret arayüzü; yerleşik fetch ve backend DTO'ları | Sağlayıcı token'ları, iş akışı durumunun otoritesi, yetkilendirme veya araç seçimi |
| Backend `AiIncidentOrchestratorApplication` | MVC/güvenlik ve worker barındırma; özellik servisleri; PostgreSQL, isteğe bağlı Redis/MCP/Temporal/OpenAI | Üretim altyapısını yönetme veya gerçek operasyonel iyileştirme |
| `UserProvisioningService`, `CurrentUserService` | Güvenilir kimlikten kullanıcı oluşturma/güncelleme ve güvenli profil okuma; `AppUserRepository` | OIDC protokol doğrulaması veya kullanıcı CRUD API'si |
| `InvestigationService`, `InvestigationStateService`, `InvestigationSubmissionService` | Sahipli kayıtlar, kısa transaction'lar, yaşam döngüsü/denetim/eylem kaydı, kabul ve yürütme koordinasyonu | Veritabanı transaction'ı içinde ağ çağrıları |
| `InvestigationExecution` | Küçük yürütme sınırı; temporal profilinde `TemporalInvestigationExecution`, diğer durumda `IncidentOrchestrator` | Rastgele, dinamik üretilmiş iş akışı grafikleri |
| `ClassificationService`, `WorkflowRoutingPolicy`, `EvidenceCollector`, `SynthesisService` | Doğrulanmış anlamsal çağrılar, sabit yönlendirme, sınırlı paralel okumalar, kaynaklı sentez/yedek sonuç; model, bağlam ve araç sözleşmeleri | Otonom yıkıcı eylemler |
| PostgreSQL | Kullanıcılar, inceleme görünümleri, JSONB kanıt/rapor, denetim ve mock eylem kaydı; Flyway/JPA/JDBC | Temporal yürütme geçmişi veya model konuşma belleği |
| Redis / `InvestigationStartLimiter` | StringRedisTemplate/Lua ile geçici kabul pencereleri | Oturumlar, önbellek, kilitler, kalıcı iş akışı/uygulama durumu |
| Temporal servisi + `InvestigationWorkflowImpl`, `InvestigationActivitiesImpl` | Kalıcı yürütme/geçmiş/zamanlayıcı; backend worker ve veritabanı görünümü Activity'leri | Anlamsal çıkarım, tarayıcı kimliği doğrulama, üretim kümesi kurulumu |
| Dokümantasyon MCP modülü / `RunbookTools`, `McpOriginFilter` | İki paketlenmiş runbook üzerinde özel Streamable HTTP yeteneği; resmi Spring AI server starter | Orkestrasyon, internet araması, rastgele dosyalar veya kimlik sağlayıcısı |
| `SpringAiIncidentReasoningModel` / `FakeIncidentReasoningModel` | Sağlayıcıdan bağımsız sınıflandırma/akıl yürütme/sentez; ChatModel veya test verisi | Sahiplik yetkilendirmesi, yönlendirme yetkisi veya eylem yürütme |
| `InvestigationTelemetry`, izleme yapılandırması | Sınırlı metrikler, gözlemler ve bağlam aktarımı; Micrometer/OTel/Temporal | Kalıcı ve eksiksiz token faturalandırması, izleme toplayıcısı veya alarmlar |

## 5. Depo Yapısı

```text
/
  AGENTS.md, README.md, compose.yaml, .env.example, .dockerignore
  backend/                         Maven wrapper, pom, Dockerfile
    src/main/java/dev/orchestrationlab/incident/
      authentication/              uygulama principal'ı; controller'lar; infrastructure/oidc
      user/                        application, domain, repository
      investigation/               application, controller, domain, repository
      orchestration/application/   yürütme sınırı, senkron orkestratör, yönlendirme/toplama/sentez
      reasoning/                   uygulama sözleşmeleri/enum'lar; model adaptörleri/prompt'lar/araçlar
      tool/                        tipli politika/yürütme; kanıt/MCP adaptörleri
      context/application/         kanıt, seçim, bütçe, oluşturucu, temizleyici
      workflow/                    Temporal arayüzü/uygulaması, Activity'ler, yürütme adaptörü
      configuration/               güvenlik, akıl yürütme, Temporal RPC politikası, izleme
      observability/               InvestigationTelemetry
      controller/                  HealthController
    src/main/resources/            application*.yml, db/migration/V1-V6, prompts/*-v1.txt
    src/test/java/                 authentication, security, user, investigation, reasoning, workflow
    src/test/resources/evaluations/incidents.tsv
  frontend/                        React/TypeScript/Vite; nginx.conf, Dockerfile, kilit dosyası
    src/                           App.tsx, Investigations.tsx, main.tsx, style.css
  mcp/documentation-server/        bağımsız Maven uygulaması, Dockerfile
    src/main/java/dev/orchestrationlab/documentation/
                                   DocumentationServerApplication, RunbookTools, McpOriginFilter
    src/main/resources/            application.yml, runbooks/recommendation.txt, payments.txt
    src/test/java/                 McpServerIntegrationTest
  docs/                            bu bağlam, özel rehberler ve tarihsel checkpoint kayıtları
```

Altyapı adaptörleri ilgili özellik paketlerinde bulunur. Her yere yayılmış ports/adapters iskeleti, genel BaseEntity/BaseRepository veya ayrı EvidenceSource sınıfı yoktur; kanıt kaynakları `ToolKind` ile temsil edilir.

## 6. Kimlik Doğrulama ve Güvenlik

1. React `/api/me` okur; 401 oturum açılmadığını gösterir. Giriş aynı origin proxy üzerinden `/oauth2/authorization/google` adresine gider.
2. `google` etkinse Spring Security `oauth2Login`, Google OIDC Authorization Code akışını yürütür (openid/profile/email). Framework'ün state/nonce/imza doğrulaması ve UserInfo işlemleri kullanıcı kaydından önce yapılır.
3. `GoogleOidcUserService`, `google` kaydını kabul eder, `OidcUserService`'e devreder, yalnızca gereken profil alanlarını `ExternalIdentity` içine aktarır ve `UserProvisioningService` çağırır.
4. Servis `(UserProvider.GOOGLE, providerSubject)` ile arar; profil anlık görüntüsünü günceller (null eski değeri temizler) veya dahili UUID oluşturur. E-posta kimlik anahtarı değildir. PostgreSQL'deki adlandırılmış benzersizlik kısıtı eşzamanlı ilk girişleri korur; yalnızca bu kısıt/SQLSTATE 23505, rollback sonrasında yeni REQUIRES_NEW transaction'ında **bir** yeniden deneme başlatır. Diğer hatalar veya ikinci denemenin hatası çağırana iletilir.
5. `ApplicationOidcUser`, `ApplicationPrincipal` uygular; `getName()` dahili UUID'dir. Spring kimlik doğrulamayı sunucu tarafındaki yerel HttpSession'da saklar ve girişte oturum kimliğini değiştirir. Kullanıcı kaydı başarısız olursa doğrulanmış oturum oluşturulmaz.
6. React `/api/me` üzerinden yalnızca `CurrentUser(id,email,displayName,avatarUrl)` alır, giriş sonrası CSRF'yi yeniler ve oturum çerezleri kullanır. localStorage erişim token'ı deposu veya uygulama JWT'si yoktur.

`DiscardingAuthorizedClientRepository`, yetkilendirilmiş istemcinin access/refresh token'larını saklamaz. Önemli ayrım: `ApplicationOidcUser`, framework OIDC ID token ve claim verilerine **sunucu tarafındaki güvenlik principal'ı içinde** erişimi delege eder. Bunlar `app_users`, denetim kayıtları veya API DTO'larına yazılmaz. Sunucu belleğinin hiçbir yerinde protokol verisi bulunmadığı iddia edilmemelidir.

`SecurityConfiguration`: GET sağlık/CSRF ve tam Google authorization/callback yolları herkese açıktır; diğer `/api/**` istekleri kimlik doğrulama ister; kalan istekler reddedilir (ERROR dispatch izinlidir). Kimliği doğrulanmamış API istekleri 401 alır; kaydedilmiş istek yönlendirmesi, form/basic giriş yoktur. HttpSessionCsrfTokenRepository güvenli olmayan istekleri korur. `POST /logout` CSRF ister, oturumu/kimlik doğrulamayı geçersiz kılar, JSESSIONID siler ve 204 döner. CurrentUserController ve CsrfController, `/api/me` ve CSRF yanıtlarına no-store ekler.

Çerezler: JSESSIONID, HttpOnly, SameSite=Lax, `/` yolu, yalnızca çerezle oturum takibi, 30 dakika hareketsizlik zaman aşımı. Secure varsayılan true; `local` ve `container` yerel HTTP için false yapar. Giriş başarı/hata URL'leri ve callback localhost:5173'e sabittir. Sahiplik filtreli okumalar dahili UUID kullanır; olmayan veya başka kullanıcıya ait inceleme 404 döner. Ortak oturum deposu yoktur; backend yeniden başlarsa yeniden giriş gerekir. Bu tek origin'li tarayıcı uygulamasında oturum yaklaşımı ayrı JWT yaşam döngüsünü gereksiz kılar.

## 7. İnceleme Yaşam Döngüsü

```text
CREATED -> ANALYZING -> COLLECTING_EVIDENCE -> REASONING -> COMPLETED
                                                    -> WAITING_APPROVAL -> COMPLETED
Her sonlanmamış durum -> FAILED; son durumlar yeni geçişleri reddeder
```

| Durum | Anlamı / değiştiren |
| --- | --- |
| CREATED | Sahipli soru InvestigationService ile kaydedildi; kalıcı başlatma hâlâ bekliyor olabilir |
| ANALYZING | StateService.begin ile sınıflandırma başladı |
| COLLECTING_EVIDENCE | StateService.classification ile sınıflandırma kaydedildi; zorunlu kaynaklar okunacak |
| REASONING | StateService.evidence ile kanıt kaydedildi; sırada sentez var |
| WAITING_APPROVAL | StateService.report ile rapor ve PENDING kaydedildi |
| COMPLETED | Eylem önerilmeyen rapor veya onay/ret/zaman aşımıyla biten bekleme |
| FAILED | Yürütme hatası sonrası StateService.fail; kaydedilmiş kısmi kanıt okunabilir kalır |

`InvestigationStatus` yukarıdaki yedi durumu tanımlar. `IncidentInvestigation` geçişleri uygular; StateService kısa transaction'ları yönetir, `@Version` çakışan yazmaları tespit eder. Temporal Activity'leri veya senkron orkestratör bu servisi çağırır. ApprovalDecision değerleri: NONE, PENDING, APPROVED, DENIED, TIMED_OUT. Karar önce approval alanını, finish sonra status alanını değiştirir. Zorunlu kaynak hatasında tipli kanıt paketi kaydedilir, toplama aşaması ilerlemez; Activity denemeleri tükenince hata görünümü yazılır. Hata görünümünü yazan Activity de başarısız olursa PostgreSQL gerçek yürütme durumunun gerisinde kalabilir; otomatik uzlaştırma garantisi yoktur.

## 8. API Yüzeyi

| Metot/yol | Erişim | İstek / yanıt ve amaç |
| --- | --- | --- |
| GET `/api/health` | Herkese açık | Girdi yok; `{status:"UP"}` HTTP kontrolü, bağımlılıklara çağrı yapmaz |
| GET `/actuator/health`, `/actuator/health/liveness`, `/actuator/health/readiness` | Herkese açık | Bileşen ayrıntıları gizli framework sağlık yanıtı; readiness yalnızca veritabanını içerir |
| GET `/api/csrf` | Herkese açık | Oturum destekli `{token,headerName}`; güvenli olmayan isteklerde belirtilen header gönderilir |
| GET `/oauth2/authorization/google` | Herkese açık; istemci kaydı gerekir | Framework Google'a yönlendirir |
| GET `/login/oauth2/code/google` | Herkese açık protokol callback'i | Framework code/state işleme ve sabit başarı/hata yönlendirmesi |
| GET `/api/me` | Oturum | Güvenli mevcut kullanıcı DTO'su; no-store |
| POST `/logout` | CSRF korumalı | Gövde yok; oturum/çerez temizleme, 204 |
| POST `/api/investigations` | Oturum + CSRF | Boş olmayan, en fazla 4000 karakterlik `{question}`; sahipli InvestigationView, Location, no-store. Normalde 201; kalıcı başlatma kesintisinde **kaydedilmiş görünüm/kimlikle 503** döner |
| GET `/api/investigations/{id}` | Sahip oturumu | InvestigationView, 200/no-store; başka kullanıcıya ait veya bulunamayan kayıt 404 |
| POST `/api/investigations/{id}/run` | Sahip + CSRF | Gövde yok; aynı kimlikle başlat/yeniden dene, görünüm 200; kabul kontrolü 429/503, Temporal erişilemezse 503; senkron yol yalnızca CREATED çalıştırır |
| POST `/api/investigations/{id}/approve` | Sahip + CSRF | Gövde yok; tipli APPROVED kararı ve görünüm; bekleme durumu çakışması 409, Temporal karar teyidi başarısızsa 503 |
| POST `/api/investigations/{id}/deny` | Sahip + CSRF | Aynı sözleşme, DENIED kararı |

InvestigationView alanları: id, question, status, createdAt, updatedAt, classification, evidence, report, approval. Nullable aşamalar iş ilerledikçe doldurulur. `IncidentReport`: investigationId, classification, evidence, findings (`IncidentSynthesis`), degraded, diagnosticSourcesSimulated. InvestigationErrors controller advice, geçersiz girdi için 400, geçersizleşmiş sahip için 401, bulunamayan kayıt için 404, geçersiz/iyimser kilitleme çakışan durum için 409 ve genel ProblemDetail döner. Eksik/geçersiz CSRF nihai yetkilendirmeden önce 403 üretir. Başarılı inceleme yanıtlarında yalnızca create/read açıkça no-store ayarlar; run/karar yanıtlarının aynı header'ları içerdiği varsayılmamalıdır. Karar teyidi veritabanı görünümünden önce gelebilir; belirsiz kararı tekrarlamadan önce durum sorgulanmalıdır. Listeleme/silme/kullanıcı CRUD endpoint'leri yoktur.

## 9. Kalıcılık Modeli

Kaynak: [db/migration](../backend/src/main/resources/db/migration). Şema yaşam döngüsü Flyway'e aittir; JPA/Hibernate eşleme ve doğrulama yapar (`ddl-auto: validate`, Open Session in View false). Create/update DDL üretmez. AppUserRepository ve InvestigationRepository doğrudan Spring Data JPA repository'leridir; sırasıyla provider/subject ve id/owner ile arar. Transaction sınırları controller'lara değil uygulama servislerine aittir; sağlayıcı/araç/Temporal çağrıları veritabanı transaction'larının dışındadır. Java yaşam döngüsü callback'leri UTC anlamı için Instant kullanır; JDBC eylem/denetim kayıtları CURRENT_TIMESTAMP varsayılanını kullanır.

| Tablo | Alanlar ve kısıtlar |
| --- | --- |
| app_users | id UUID PK; provider varchar(32), provider_subject varchar(255) NOT NULL; nullable TEXT email/display_name/avatar_url; created_at/updated_at NOT NULL timestamptz; benzersiz `uk_app_users_provider_subject(provider,provider_subject)`; **e-posta benzersizliği yok** |
| incident_investigations | id UUID PK; owner_id UUID NOT NULL FK app_users; question varchar(4000) NOT NULL/boş olmama kontrolü; status varchar(32) NOT NULL/yedi durum kontrolü; created_at/updated_at NOT NULL timestamptz; version bigint NOT NULL varsayılan 0; nullable classification/evidence/report JSONB; approval varchar(24) NOT NULL varsayılan NONE/enum kontrolü; start_requested boolean NOT NULL varsayılan false; start_attempts integer NOT NULL varsayılan 0/negatif olmama kontrolü; nullable approval_requested_at timestamptz |
| mock_remediation_actions | investigation_id UUID PK/FK incident_investigations; action varchar(64) NOT NULL/sabit restart kontrolü; executed_at NOT NULL timestamptz varsayılan CURRENT_TIMESTAMP; inceleme başına tek mantıksal mock eylem |
| investigation_audit | id bigint generated identity PK; investigation_id UUID NOT NULL FK; stage varchar(32), workflow_version/prompt_version varchar(16), tools varchar(200), outcome varchar(32), created_at timestamptz, tamamı NOT NULL; unique(investigation_id,stage,outcome) yeniden yürütme tekrarlarını engeller |
| flyway_schema_history | Flyway migration metadata/checksum kayıtları; JPA uygulama entity'si değildir |

Migration sırası: `V1__create_app_users.sql`; `V2__create_investigations.sql` (sahip indeksi); `V3__persist_investigation_context.sql` (version/classification/evidence); `V4__investigation_report_and_approval.sql` (rapor/onay/mock eylem kaydı); `V5__durable_start_requests.sql` (niyet/denemeler, bekleyen başlatmaların kısmi indeksi); `V6__investigation_audit.sql` (onay zamanı/denetim).

Uygulama tabloları Google access/refresh/ID token'larını, ham OIDC claim'lerini, gizli düşünce zincirini, model konuşma dökümlerini veya temizlenmemiş ham operasyonel logları saklamaz. İlk soru **saklanır**; sınırlı ve temizlenmiş kanıt, sonuçlar ve profil verileri yine hassas olabilir. Maskeleme bütün sırların kaldırıldığı garantisini vermez. Token/maliyet kayıt defteri veya saklama/silme politikası uygulanmamıştır.

## 10. LLM Entegrasyonu

Spring AI **2.0.1**, ChatModel'i `SpringAiIncidentReasoningModel` üzerinden uyarlar; bu sınıf `IncidentClassificationModel`, `IncidentSynthesisModel` ve `IncidentReasoningModel` uygular. `ReasoningConfiguration`, `ai` profilinde bu adaptörü seçer; diğer durumda `FakeIncidentReasoningModel` açıkça etiketli deterministik simülasyon sağlar. Temel yapılandırma model otomatik yapılandırmasını kapatır; ai, OPENAI_API_KEY/AI_CHAT_MODEL ile OpenAI sohbetini açar. İletişim zaman aşımı 20s, iletişim max-retries 0, Spring AI retry max-attempts 1'dir.

`PromptCatalog` classpath'ten yalnızca izinli `classification-v1.txt`, `synthesis-v1.txt`, `tool-investigation-v1.txt` dosyalarını yükler. Sınıflandırma/sentez ChatClient'ları StructuredOutputValidationAdvisor ile bir şema düzeltme tekrarı, tipli record dönüşümü ve isteğe bağlı sağlayıcıya özgü yapılandırılmış çıktı kullanır (varsayılan false). Uygulama anlamsal doğrulaması ayrıca iki denemeye izin verir. İç içe sınırlar, Activity düzeyindeki yeniden denemeden önce aşama başına dört sağlayıcı isteğine kadar çıkabilir; tek genel yeniden deneme bütçesi olarak anlatılmamalıdır.

Mevcut bütünleşik akış: prompt -> tipli sınıflandırma -> Java yönlendirme/toplama -> temizlenmiş kanıt -> sentez prompt'u -> tipli bulgu/kaynak doğrulama. Sentez araç çalıştırmaz.

Ayrı ve sınırlı `reason(question, ToolSession)` gösterimi:

```text
sabit sistem prompt'u + kullanıcı mesajları -> model -> araç adı/JSON argümanları
 -> Spring AI ToolCallingManager/callback dönüşümü
 -> Java ToolSession/ToolExecutionService sahiplik + izin listesi + girdi kontrolleri
 -> adaptör okuması -> temizlenmiş ToolResult -> model konuşması -> kısa son metin
```

**LLM Java metotlarını doğrudan çalıştırmaz.** Doğrulamadan sonra kayıtlı callback'leri Java çalıştırır. Yalnızca izinli callback'ler modele sunulur; otomatik araç döngüsü kaydı kapalıdır. Sınırlar: dört model turu, sekiz araç çağrısı, en fazla 8000 karakter son metin. Son çıktı yapılandırılmış rapor değil metindir; kalıcı sınıflandırma/toplama/sentez sırası bu gösterimi çağırmaz. Varsayılan başlangıç/testler canlı sağlayıcı gerektirmez. Çevrimdışı kalite testleri canlı OpenAI doğruluğunu, gecikmesini veya maliyetini kanıtlayamaz.

## 11. Araç Sistemi

`SpringAiTools` tam dört tipli callback kaydeder; deterministik toplayıcı aynı yürütme servisini doğrudan kullanır.

| Callback / ToolKind | Amaç | Mevcut adaptör |
| --- | --- | --- |
| searchLogs / LOGS | Teşhis log özetlerini okuma | StubEvidenceAdapter simülasyonu |
| queryDatabase / DATABASE | Sınırlı veritabanı teşhisini okuma | StubEvidenceAdapter simülasyonu; uygulama tablolarını sorgulamaz veya üretilmiş SQL çalıştırmaz |
| inspectMetrics / METRICS | Teşhis metrik özetlerini okuma | StubEvidenceAdapter simülasyonu |
| searchDocumentation / DOCUMENTATION | Servis runbook kanıtını okuma | mcp profilinde McpDocumentationAdapter; aksi durumda stub |

Ortak `ToolInput(service,topic,limit)`: izinli servisler recommendation/payments; Topic değerleri ERRORS, CONNECTIONS, TIMEOUTS, RUNBOOK, LATENCY; limit 1–10. Adaptör çıktısı List<String>; `ToolResult(source,evidence,failure)` başarı veya FORBIDDEN, INVALID_ARGUMENT, TIMEOUT, UNAVAILABLE, BUSY bildirir.

Güvenilir `ToolContext(ownerId,investigationId,allowedTools)` ve sahiplik kapsamlı repository araması adaptör çağrısından önce yürütmeyi sınırlar. ToolSession izin listesinin genişletilmesini engeller ve çağrı sayısını sınırlar. ToolExecutionService dört sanal worker thread'i, 32 kapasiteli kuyruk, yapılandırılabilir varsayılan 2s süre sınırı, zaman aşımında iptal ve genel hata sonuçları kullanır. Çıktı en fazla input.limit öğedir; her öğe temizlendikten sonra 1000 karaktere kırpılır. Adaptörün iletişim süre sınırları iptali tamamlar; kesinti tek başına gelecekteki bütün dış entegrasyonların işi durduracağını garanti etmez.

EvidenceCollector'ın ayrı dört worker/32 kuyruk havuzu vardır. Her okumayı yalnızca TIMEOUT/UNAVAILABLE için **bir kez** tekrarlar; kanıtları birleştirmek için toplam 6s sınır uygular. ToolExecutionService kendi başına yeniden deneme yapmaz. Zorunlu kaynak eksikse kanıt paketini taşıyan RequiredEvidenceUnavailable fırlatılır; isteğe bağlı hata degraded işaretlenir. Soru başka bir servisi adlandırsa bile toplayıcı recommendation/ERRORS/limit 4 gönderir. Veritabanı aracı girdisinde SQL alanı yoktur; simülasyon değiştirilirken sınırlı teşhis sorgusu sözleşmesi korunmalıdır.

## 12. MCP

```text
izinli DOCUMENTATION okuması -> McpDocumentationAdapter
 -> resmi Java SDK Streamable HTTP istemcisi -> özel /mcp sunucusu
 -> initialize -> listTools -> sabit searchDocumentation(input) -> paketlenmiş içerik
```

Backend resmi Spring AI MCP istemci bağımlılığını kullanır, ancak otomatik istemci başlangıcını kapatır. Açık adaptör her çağrıda gerektiğinde bağlanır; araç süre sınırı içinde istek ve başlangıç için 1s zaman aşımı kullanır. Keşifte searchDocumentation bulunduğunu doğrular, yalnızca bu adı çağırır, isError yanıtlarını reddeder, her metin yükünü 12000 karakterle sınırlar, JSON List<String> ayrıştırır ve öğe sayısını sınırlar. Yanlış yapılandırma, eksik yetenek veya uzak servis hatası tipli ve sınırlı araç hatasına dönüşür; isteğe bağlı dokümantasyon kesintisi incelemeyi durdurmak yerine kalitesini düşürebilir.

Bağımsız sunucu Spring AI MVC MCP server, STREAMABLE/SYNC ve sabit araç yeteneği kullanır; resources/prompts/completion kapalı, sunucu istek zaman aşımı 3s'dir. RunbookTools service/topic/limit doğrular; paketlenmiş iki dosyadan birinin ilk boş olmayan satırlarını alır, satır başına 900 karakter sınırı uygular. Topic doğrulanır ancak anlamsal arama veya filtreleme sağlamaz. McpOriginFilter, `/mcp` yolundaki tarayıcı Origin erişimini 403 ile reddeder. MCP kimlik doğrulaması/TLS yapılandırılmamıştır; özel ve güvenilir ağda konumlandırılmalıdır. Backend URL yapılandırması host içeren http/https kabul eder; userinfo/query/fragment içeremez, kullanıcı/model girdisi URL'yi seçemez. Bu kontrol yönetici yapılandırması için genel bir host izin listesi değildir.

Tool calling, modelin callback çağrısı önermesidir; MCP yetenek keşfi/iletişim protokolüdür; orkestrasyon uygulama sırası/durumu/politikasıdır. **MCP orkestratör değildir.** Keşif yetki vermez; sunucu iş akışı başlatmaz veya iyileştirme eylemi yürütmez.

## 13. Bağlam Mühendisliği

Mevcut nesneler: Evidence(id,source,summary); kaynak enum'u ToolKind; SourceFailure içeren EvidenceBundle(items,failures,truncated); ContextSelection(sources,required); ContextBudget; ContextBuilder; EvidenceSanitizer. Ayrı EvidenceSource tipi yoktur.

Akış: Java yönlendirmesi -> önce zorunlu kaynaklar, kalanlar enum adına göre -> maxTools seçimi -> yetkili paralel okumalar -> temizleme -> seçilen kaynaklara göre filtreleme -> boş özetleri çıkarma -> öğe/kaynak/toplam karakter sınırları -> kaynak-sıra kimlikli atıflar -> tipli paket. Sıralama zorunlu kaynak önceliğine dayalı deterministik sıralamadır; anlamsal ilgililik puanı veya vektör araması değildir. Kimlikler kaydedilmiş paket içinde sabittir; incelemeler arasında küresel kimlik değildir.

`app.context` varsayılanları: 4 araç, 12 kanıt öğesi, araç/kaynak sonucu başına 2000 karakter, toplam 8000 özet karakteri. Geçerli aralıklar: araç 1–4, öğe 1–100, kaynak başına 1–10000, toplam 1–40000. Araç katmanında daha önce öğe başına 1000 karakter uygulanır. Kırpma ve kaynak hataları bundle.degraded değerini true yapar. Atıf biçimi/sistem prompt'u ve ayrı sınırlanan 4000 karakterlik soru ek yer kaplar; bunlar karakter bütçeleridir, token/bağlam penceresi garantisi değildir.

Temizleyici kontrol karakterlerini kaldırır; yaygın password/key/secret/access-token/authorization kalıplarını, sk-key biçimlerini ve e-posta adreslerini maskeler. Eksiksiz koruma sağlamaz. Sentez seçilmiş, temizlenmiş özetleri kimliklerle ve UNTRUSTED EVIDENCE işaretiyle alır; ham loglar doğrudan aktarılmaz. ContextBuilder.render vardır, ancak canlı sentez adaptörü paketten kendi kimlik etiketli prompt'unu oluşturur.

HTTP oturum kimliği, kalıcı inceleme aşaması/kanıtı, çağrıya özgü geçici model mesajları ve Temporal geçmişi ayrı rollerdedir. **Uzun süreli bellek bilinçli olarak uygulanmamıştır.** Saklanan incelemeler kayıttır; eski raporlar yeni incelemelere otomatik getirilmez.

## 14. Yapılandırılmış Çıktı ve Yönlendirme

`IncidentClassification(incidentType,severity,requiredTools,summary)`: null olmayan enum'lar, boş olmayan ve en fazla dört araç içeren küme, boş olmayan ve en fazla 1000 karakterlik özet gerektirir. IncidentType: DATABASE, APPLICATION, DEPENDENCY, PERFORMANCE, UNKNOWN. Severity: LOW, MEDIUM, HIGH, CRITICAL. Araçlar rastgele handler isimleri değil Set<ToolKind>'dır.

```text
model sınıflandırması -> şema/record doğrulaması -> ClassificationService
 -> WorkflowRoutingPolicy izin listesi -> Java Route(tools,requiredEvidence)
```

| Tür | İzinli kaynaklar | Java'nın zorunlu tuttuğu kaynak |
| --- | --- | --- |
| DATABASE | LOGS, DATABASE, METRICS, DOCUMENTATION | DATABASE |
| APPLICATION, UNKNOWN | LOGS, DOCUMENTATION | LOGS |
| DEPENDENCY | LOGS, METRICS, DOCUMENTATION | LOGS |
| PERFORMANCE | LOGS, METRICS, DOCUMENTATION | METRICS |

Java, modelin doğrulanmış kümesine zorunlu kaynağı ve DOCUMENTATION ekler. ClassificationService bir kez yeniden dener, sonra temkinli özet ve açık degraded Result ile UNKNOWN/LOW/LOGS+DOCUMENTATION kullanır. Üretilmiş workflow/sınıf adları çalıştırılmaz.

`IncidentSynthesis`: özet <=1500 karakter; nedenler/eylemler ayrı ayrı <=5 metin, her biri boş olmayan <=500 karakter; <=100 kanıt kimliği, her biri <=64 karakter; ProposedAction NONE veya RESTART_RECOMMENDATION_SERVICE. SynthesisService her atfı verilen kimliklere karşı denetler; kanıt varsa atıf ister. İki denemeden sonra temkinli DEGRADED/manuel inceleme/NONE sonucuna geçer. Atfın varlığı çıkarımın doğruluğunu kanıtlamaz. Temporal, sınıflandırmanın yedek sonuç olduğunu fallback record'uyla eşitlikten anlar; degraded bayrağı ayrıca saklanmaz.

## 15. Orkestrasyon Kalıpları

| Uygulanan kalıp | Kullanıldığı yer |
| --- | --- |
| Ardışık / koşullu | Sınıflandır, topla, analiz et; yalnızca doğrulanmış restart önerisinde onay dalı |
| Fan-out / paralellik / fan-in | EvidenceCollector bağımsız salt okunur kaynakları paralel başlatır, sınırlı tek paket oluşturur |
| Sınırlı yeniden deneme | Şema advisor tekrarı, anlamsal iki deneme, salt okunur araç için bir tekrar, üç Activity denemesi, sabit kimlikli sınırlı başlatma dispatcher'ı |
| Zaman aşımı | Tarayıcı, OpenAI/MCP iletişimi, araç/toplama, Temporal RPC/Activity/yürütme ve kalıcı onay bekleme |
| Yedek davranış | Temkinli sınıflandırma, manuel inceleme sentezi, isteğe bağlı kanıt kalitesinin düşmesi |
| İnsan onayı | Sahibin CSRF isteği ve tipli Temporal Update; yalnızca mock tamamlanma |
| Araç yetkilendirmesi | Route/ToolContext/ToolSession, sahiplik kapsamlı arama ve girdi izin listesi |

Model yedek davranışı yerel ve temkinli bir sonuçtur; ikinci canlı sağlayıcı değildir. Circuit breaker **uygulanmamıştır**: test verileri ve küçük isteğe bağlı içerik için sınırlı hata yönetimi yeterlidir; çağrı bastırma yalnızca kanıtlanmış dış servis ihtiyacında eklenmelidir. Otonom çok ajanlı çalışma zamanı yoktur.

## 16. Temporal

`application-temporal.yml`, workflow uygulamasını ve `investigationActivities` bean'ini `investigation-worker` worker'ına kaydeder. Kuyruk **incident-investigations-v1**, namespace **default**, workflow kimliği **investigation-<UUID>**, aynı kimliğin yeniden kullanımı REJECT_DUPLICATE'dır. Worker backend içinde çalışır. `TemporalPolicyConfiguration` zamanlanmış dispatch'i açar; SDK RPC 5s/long-poll 10s sınırı koyar. Açık start/Update çağrılarında ayrıca toplam 5s gRPC süre sınırı vardır.

```text
inceleme + start_requested kaydet -> sabit kimlikle başlat -> Temporal geçmişi
  -> classify(owner,id) Activity -> collect(owner,id) Activity
  -> analyze(owner,id) Activity
     -> onay yok: workflow tamamlanır
     -> WAITING_APPROVAL: Workflow.await(kalıcı zaman aşımı, decision != null)
          -> doğrulanmış sahip Update'i APPROVED/DENIED veya TIMED_OUT
          -> complete(owner,id,decision) Activity -> workflow tamamlanır
  ActivityFailure -> fail(owner,id) Activity
```

Workflow arayüzü investigate, validateDecision ile decide Update ve phase Query sunar. **Workflow kodu deterministik kalmalıdır.** LLM çağrıları, araç/MCP/ağ işleri, veritabanı okumaları/yazmaları ve zaman damgaları Activity'lerde/uygulama servislerinde olmalıdır; workflow Activity sırasını ve Temporal zamanlayıcılarını yönetir. Update doğrulayıcı sahip, bekleme aşaması, onay/ret ve önceki niyetle çakışmama kontrolü yapar. Bekleme sürerken aynı niyet tekrarlanabilir. Update handler kalıcı kararı ayarlar; veritabanı görünümü daha sonra güncellenebilir.

Activity start-to-close 3m, schedule-to-close 10m, en fazla üç deneme, ilk yeniden deneme aralığı 1s/en fazla 3s. Workflow yürütme süresi 2d; açık workflow retry politikası yoktur. Onay varsayılan 1h, pozitif ve en fazla 1d; deterministik replay için workflow girdisine aktarılır. Activity'ler kaydedilmiş aşamaları atlar; eylem kaydı+tamamlanma transaction'ı tekrar yürütme/denemeyi korur. Dış model/okuma çağrıları Activity tekrarıyla yine yapılabilir ve maliyet doğurabilir.

Create, RPC'den önce başlatma niyetini kayıtla aynı transaction'da commit eder. Dispatcher her 5000ms'de attempts <12 olan en fazla on bekleyen kaydı seçer, dispatch öncesi sayacı artırır, AlreadyStarted durumunu kabul sayar ve kabul sonrası niyeti temizler. Hatalı dispatch bekler; sahipli açık `/run` sayacı yeniler. Tükenince manuel tekrar gerekir; takılan başlangıç alarmı yoktur. Normal replay tamamlanan Activity sonuçlarını korur, backend yeniden başlasa da onay bekleme sürer; yerel HttpSession korunmaz. Temporal CLI SQLite named volume'u geçmişi PostgreSQL görünüm volume'undan ayrı saklar. Yedekleme, uzlaştırma, workflow uyumlu dağıtım/sürümleme ve üretim Temporal işletimi açık çalışmalardır.

## 17. İnsan Onayı ve Eylemler

Doğrulanmış sentez RESTART_RECOMMENDATION_SERVICE öneriyorsa, sentez degraded değilse ve soru büyük/küçük harf duyarsız `recommendation` içeriyorsa Java onay ister. Diğer hedef/öneriler politikadan geçmez. Severity onay eşiği değildir. Kanıt/sınıflandırma kalitesinin düşmesi tek başına bu öneriyi **engellemez**; toplam rapor yine degraded işaretlenir. Uygulanmamış daha katı bir kuralı belgelemek yerine bu ayrımı koruyun.

Yalnızca sahip oturum+CSRF API'si ve workflow doğrulayıcısıyla onaylayabilir/reddedebilir. PostgreSQL rapor/PENDING/approval_requested_at kaydı beklemeden önce oluşur; Temporal geçmişi kalıcı niyeti/zamanlayıcıyı taşır. Onay APPROVED kaydeder, `mock_remediation_actions` içine ON CONFLICT DO NOTHING ile yazar, ardından aynı transaction'da COMPLETED yapar. Ret/zaman aşımı DENIED/TIMED_OUT kaydeder ve eylemsiz tamamlar. Tamamlanmış aynı kararın tekrarı mevcut görünümü döndürebilir. Kabuk, restart API'si veya gerçek değişiklik yoktur. **Üretimde yıkıcı eylemler etkin değildir.** Gelecekteki gerçek eylem dış sistemde idempotans protokolü gerektirir; mevcut veritabanı kaydı tek başına uzaktaki etkinin tam bir kez gerçekleşmesini garanti edemez.

## 18. Redis

Mevcut görev: isteğe bağlı, sahip başına create/run kabul kontrolü. Atomik Lua INCR ve ilk artışta PEXPIRE; anahtar `investigation-start:v1:<internalUUID>`. İlk istekte başlayan PT1M penceresinde varsayılan beş kabul; reddedilen denemeler sayacı artırır ama süreyi uzatmaz. Limit 1–100, pencere pozitif ve en fazla 1h. Redis erişilemez/null sonuç => 503 ve kapalı kabul politikası; limit üstü => 429. Bu limiter Retry-After yanıt header'ı ayarlamaz.

Temel profil kabul kontrolünü kapatır, container profili açar. Create ve açık run sayılır; dahili dispatch/read/approve/deny sayılmaz. Geçersiz create, soru doğrulamasından önce kabul hakkı tüketebilir. Redis kalıcılığı kapalıdır; yeniden başlatma pencereleri sıfırlar. Redis ana kalıcılık, kimlik doğrulama deposu, workflow durumu, uzun süreli bellek, önbellek veya dağıtık kilit değildir. Etkin kabul kontrolü Redis'e bağlı olsa da readiness yalnızca veritabanını kontrol eder; toplu sağlık Redis'i içerir.

## 19. Gözlemlenebilirlik

`InvestigationTelemetry`, incident.tool, incident.llm, incident.mcp gözlemlerini kaydeder. Kategori/araç/sonuç etiketleri sınırlıdır; model aşamalarında model/provider/prompt.version v1 bulunur; inceleme UUID'si yalnızca yüksek kardinaliteli trace verisidir. Özel metrikler: `incident.tool.calls`, `incident.tool.duration`, `incident.tool.failures`, `incident.timeouts`, `incident.retries`, `incident.model.fallback` (sentez), `incident.investigation.starts`, `incident.investigations`, `incident.investigation.duration`, `incident.approval.wait`. Sınıflandırma yedek sonucu sentez fallback metriğinde sayılmaz. Araç metrikleri fiziksel denemeleri sayar; sonlanma/kabul metrikleri veritabanı commit'inden sonra kaydedilir.

Gözlemlenen ChatClient'lar Spring AI model telemetrisini kullanır; gerçek yanıt varsa sağlayıcının sunduğu kullanım bilgisi de buna dahildir. Sahte adaptörler uydurma kullanım üretmez. İnceleme başına kalıcı token defteri veya para tahminleyici yoktur. Sabit prompt/workflow v1 metadata'sı denetim aşaması/sonuç satırlarına yazılır; denetim her yeniden denemenin/sağlayıcı isteğinin eksiksiz kaydı değildir.

Boot HTTP gözlemleri, `WorkflowTracingConfiguration` OTel/OpenTracing köprüsü ve resmi Temporal interception, workflow/Activity'ler arasında bağlam taşır. ContextSnapshot paralel executor'lara scope taşır; MCP aktarım header'ları ekler. Depo yapılandırması prompt/completion/araç içeriği dışa aktarımını açmaz; özel etiketler/denetim ham içerikten kaçınır. İçerik seçeneklerini kapalı tutun, gelecekteki exporter'ları inceleyin. Metrikler süreç içinde tutulur ve yeniden başlatmada sıfırlanır; Actuator yalnızca sağlık sunar. OTLP metrik aktarımı isteğe bağlı, örnekleme 1.0'dır; Compose'da collector, dashboard veya alarm servisi yoktur. Trace'lerin şu anda bir collector'a ulaştığı varsayılmamalı; gerçek dağıtımda izleme/aktarım yapılandırması incelenmelidir.

## 20. Prompt Injection / AI Güvenlik Modeli

Sürümlü sistem politikaları talimatları güvenilmeyen sorulardan/kanıttan ayırır. Java tipli şemalar, route/callback izin listeleri, güvenilir sahip bağlamı, argüman/çıktı sınırları, eldeki imkânlarla temizleme, kaynak/atıf kontrolleri ve tek mock değişiklik için açık sahip onayıyla etkiyi sınırlar. React metinleri escape ederek render eder; model HTML'i çalıştırmaz. Model kontrollü SQL, HTTP hedefi, dosya yolu veya kabuk komutu yoktur. MCP sunucular arasındadır; tarayıcı Origin isteklerini reddeder.

Bu savunmalar riski azaltır; **prompt injection matematiksel olarak ortadan kaldırılmaz**. Maskeleme sırları kaçırabilir, kullanıcı girdisi saklanır, geçerli atıflar yanlış sonuçlara eşlik edebilir; güvenilen altyapı sınırının ele geçirilmesi ayrı kontroller gerektirir. Model önerisi hiçbir zaman yürütme yetkisi vermez.

## 21. Test Stratejisi

| Alan | Mevcut testler / mekanizma |
| --- | --- |
| Kalıcılık/kullanıcı kaydı | UserPersistenceIntegrationTest: PostgreSQL 18 Testcontainers @ServiceConnection; Flyway/Hibernate/kısıtlar, profil güncelleme, e-posta ayrımı, eşzamanlı ilk giriş ve sınırlı tekrar; H2 yok |
| Güvenlik/OIDC | SecurityRequestTest, GoogleOidcUserServiceTest, OAuthSecurityFlowTest: MockMvc ve loopback test sağlayıcısı, state/nonce/imza/oturum/CSRF/çıkış/güvenli DTO |
| Sahiplik/durum/onay/eylem kaydı | InvestigationIntegrationTest, DurableApiIntegrationTest, SubmissionTest: gerçek PostgreSQL, doğrulanmış test kimlikleri, geçersiz geçişler, atomik başlatma niyeti, kararlar ve tekrar eylem kaydı |
| Spring AI/araçlar/yönlendirme | ToolCallingTest, ClassificationTest: sahte ChatModel ile gerçek framework şema dönüşümü/callback döngüsü, politika/sınırlar/bilinmeyen yetenekler |
| Bağlam/orkestrasyon | ContextTest, OrchestrationTest: sıralama/maskeleme/sınırlar, paralel okumalar/birleştirme, zorunlu/isteğe bağlı hatalar, atıf tekrarı/yedek sonuç |
| MCP | McpServerIntegrationTest: gerçek Streamable keşif/çağrı, içerik sınırları; McpUnavailableTest: sınırlı uzak servis hatası |
| Temporal | InvestigationWorkflowTest, ActivityTimeoutTest: desteklenen TestWorkflowEnvironment, tekrar/süre sınırı/zaman atlama, sahip Update'leri, onay süresi/ret/onay |
| Redis/telemetri/değerlendirme | RateLimitTest gerçek Redis 8 container'ı ve kapalı kabul; TelemetryTest sınırlı metrikler; EvaluationTest incidents.tsv'deki beş senaryo ve sürümlü politika sözleşmeleri |
| Açıkça seçilen smoke kontrolleri | TemporalRestartSmoke (gerçek servis/worker yeniden başlatma); McpInvestigationSmoke (uzak içerik + PG); ContainerStackSmoke (altı servis/backend yeniden başlatma/mock eylem) |

Standart backend `mvnw.cmd --batch-mode verify` ve bağımsız MCP `../../backend/mvnw.cmd --batch-mode verify`, JDK 25, PG/Redis testleri için Docker ve ilk çalışmada bağımlılık/image erişimi gerektirir. Frontend `npm ci`, `npm run build` TypeScript/üretim paketini doğrular; frontend tarayıcı test paketi yoktur. Standart testler `.env`, Google/OpenAI kimlik bilgisi veya tam Compose stack'i gerektirmez. Smoke sınıfları `-Dtest=<SmokeClass> test` ile açıkça seçilir; normal Surefire isimlendirmesi dışındadır. Tam stack smoke yerel altyapı kimlik bilgilerini kullanır, sentetik sahip oluşturur ve güvenilir test workflow çağrısı yapar; gerçek Google tarayıcı girişi testi değildir. [Bütünleşik uygulama komutlarına](capstone.md) bakın.

Geçmiş doğrulama [2026-10-07](capstone-verification.md): backend 83 + MCP 2 + üç açık smoke çalıştırması = **88 başarılı test çalıştırması**; frontend derlemesi, temiz migration/doğrulama ve altı sağlıklı servis. Bu tarihli bir kayıttır; **bu dokümantasyon değişikliğinde testlerin veya servis sağlığının yeniden kontrol edildiği iddiası değildir**. Canlı Google girişi/OpenAI değerlendirmesi gerçek dış kimlik bilgileri gerektirir; o kayıtta doğrulanmamıştır.

## 22. Docker / Yerel Çalışma Ortamı

Kaynak: [compose.yaml](../compose.yaml). Altı servis özel Compose ağını paylaşır; yayınlanan portlar 127.0.0.1'e bağlanır.

| Servis | Port / depolama | Sağlık ve bağımlılık politikası |
| --- | --- | --- |
| frontend | Host 5173 -> nginx 8080; statik paket | wget `/`; sağlıklı backend bekler |
| backend | Özel 8080; backend worker dahildir | curl readiness; sağlıklı postgres/redis/temporal/MCP bekler |
| postgres | Host 5432; postgres:18; /var/lib/postgresql konumunda postgres_data | pg_isready |
| redis | Host 6379; redis:8; parola, RDB/AOF yok | Kimlik doğrulamalı redis-cli PONG |
| temporal | Host 7233 API /8233 UI; temporalio/temporal:1.9.1 start-dev; /home/temporal konumunda temporal_data | temporal operator cluster health |
| documentation-mcp-server | Özel 8081; paketlenmiş runbook'lar | curl Actuator health |

Java uygulama container'ları Temurin 25 çok aşamalı derleme/checksum doğrulamalı wrapper ve root olmayan çalışma UID 10001 kullanır. Frontend Node 24/npm12.2 ile derlenir, yetkisiz nginx 1.29 ile sunulur. MCP Dockerfile ENV ile 0.0.0.0'a bağlanır. Uygulama image'ları testler atlanarak derlenir; testleri ayrı çalıştırın. PG/Temporal volume'ları compose down sonrasında kalır; Redis geçicidir. Compose down --volumes iki kalıcı depoyu da siler. Image ana sürüm etiketleri değişmez digest değildir. Yerel HTTP/güvenilir ağ yapılandırmasıdır; üretim TLS/HA kurulumu değildir.

Container isteği: tarayıcı -> nginx :5173 -> özel backend -> servisler. Host geliştirme: Vite :5173, `/api`, `/oauth2`, `/login/oauth2`, `/logout` yollarını localhost:8080'e proxy eder (nginx daha geniş `/login/` yolunu proxy eder). Backend `local,temporal,mcp`, loopback PG/Redis/Temporal ve bağımsız MCP localhost:8081 ile konuşur; Compose MCP portu **yayınlanmaz**. Host geliştirmede belgelenen bağımsız MCP süreci veya bilinçli olarak erişilebilir bir yapılandırma gerekir. React göreli fetch, inceleme isteklerinde 10s sınır, önce 1s sonra 2s aralıkla durum sorgulama kullanır; farklı origin'ler arasında token alışverişi yoktur. Nginx backend Docker DNS'ini yeniden çözümler. Çalıştırma için [README](../README.md) ve [bütünleşik uygulama başlangıcına](capstone.md) bakın; `.env` Compose'da otomatik yüklenir, Maven/IDE'de yüklenmez.

## 23. Ortam Değişkenleri ve Uygulama Yapılandırması

Gerçek sırlar dokümanlara veya Git'e yazılmaz. Şablon [.env.example](../.env.example), `.env` ise ignore edilir ve Docker build context'inden çıkarılır. Ortam değişikliği mevcut PG volume'undaki kimlik bilgilerini değiştirmez.

| Grup | Gerçek değişkenler / varsayılanlar |
| --- | --- |
| PostgreSQL | POSTGRES_DB, POSTGRES_USER, POSTGRES_PASSWORD local/container datasource ve Compose için zorunlu; yerel başlangıç ve uygulama aynı hesabı kullanır |
| Redis | REDIS_PASSWORD local/container ve Compose için zorunlu; bağlantı/komut zaman aşımı 2s |
| Google | GOOGLE_CLIENT_ID, GOOGLE_CLIENT_SECRET; yalnızca google kaydı etkinse gerekli; callback localhost:5173'e sabit |
| OpenAI | ai etkinse OPENAI_API_KEY; anlamlı canlı model seçimi için AI_CHAT_MODEL gerekli (Compose varsayılanı offline-fixture telemetri/test etiketi, desteklenen canlı model seçimi değildir) |
| Profiller | Uygulama için SPRING_PROFILES_ACTIVE; BACKEND_PROFILES Compose interpolasyonu, varsayılan container,temporal,mcp. google/ai gerekli kimlik bilgileriyle bilinçli eklenir |
| Temporal | TEMPORAL_ADDRESS varsayılan localhost:7233; Compose temporal:7233 yapar. APPROVAL_TIMEOUT varsayılan PT1H; namespace/kuyruk yapılandırmada sabit |
| MCP sunucusu | MCP_BIND_ADDRESS varsayılan localhost (Dockerfile 0.0.0.0), MCP_PORT varsayılan 8081 |
| Kabul kontrolü | INVESTIGATION_RATE_LIMIT_ENABLED temelde false; INVESTIGATION_RATE_LIMIT varsayılan 5; INVESTIGATION_RATE_WINDOW varsayılan PT1M. Container YAML, temel ortam placeholder değerini geçersiz kılarak app.rate-limit.enabled değerini açıkça true yapar |
| Telemetri | OTLP_METRICS_ENABLED varsayılan false; collector/endpoint/içerik bayrakları şablon dışında açık Spring/OTel yapılandırması gerektirir |

Diğer anlamlı Spring özellikleri (.env.example'da özel değişken değildir): `app.mcp.documentation-url` varsayılan http://localhost:8081, container'da sabit http://documentation-mcp-server:8081; `app.ai.native-structured-output` false; `app.tools.timeout` 2s; `app.context.max-evidence-items` 12, `max-characters-per-tool-result` 2000, `max-total-characters` 8000, `max-tools` 4; `app.workflow.dispatch-delay` 5000 milisaniye; yukarıdaki rate-limit/workflow özellikleri. Bunları Spring property kaynakları veya standart relaxed-binding ortam adlarıyla (örneğin APP_MCP_DOCUMENTATIONURL) ayarlayın; kodun okumadığı özel şablon değişkenleri uydurmayın. Spring server/session/datasource/management ayarları normal property kaynaklarıyla değiştirilebilir.

Compose profile/DB/Redis/Temporal/Google/OpenAI/model/approval-timeout/rate-limit ayarlarını açıkça aktarır. Her host değişkenini veya olası her Spring property'sini otomatik **aktarmaz**; aktarılmayan bir ayarı değiştirirken açık container environment eşlemesi veya başka property kaynağı ekleyin. Daha yüksek öncelikli `app.rate-limit.enabled` ayarı container kabulünü değiştirebilir; temel placeholder değişkenini değiştirmek profilin sabit değerini geçersiz kılmaz.

## 24. Simülasyon ve Gerçek Bileşenler

| Bileşen | Mevcut durum |
| --- | --- |
| PostgreSQL/Flyway/JPA | Gerçek veritabanı/migration/eşleme |
| Redis | Etkinse gerçek kabul mekanizması; geçici |
| Temporal | Gerçek yerel kalıcı servis; standart testlerde test ortamı |
| MCP protokolü/istemcisi/sunucusu | mcp profilinde gerçek Streamable HTTP sınırı |
| Dokümantasyon kaynağı | Sabit eğitim amaçlı yerel içerik; canlı üretim dokümantasyonu/arama değil |
| Log/veritabanı teşhisi/metrik adaptörleri | Etiketli simülasyon verileri; gerçek uygulama PostgreSQL'inden ayrı |
| mcp olmadan dokümantasyon | Simülasyon adaptörü |
| İyileştirme | Benzersiz mock eylem kaydı; gerçek restart yok |
| Google girişi | Framework akışı uygulanmış; gerçek giriş için dış kimlik bilgisi gerekir |
| OpenAI | Spring AI adaptörü uygulanmış; ai profili/geçerli model/kimlik bilgisi gerekir |
| Varsayılan sınıflandırma/sentez | Deterministik çevrimdışı test verisi; canlı LLM değil |
| Teşhis raporu | Gerçek MCP iletişiminde bile mevcut yürütme yollarında diagnosticSourcesSimulated daima true |
| Gözlemlenebilirlik | Gerçek enstrümantasyon; exporter/collector işletimi ve canlı sağlayıcı kullanımı ortama bağlı |

## 25. Bilinen Kısıtlar

- Operasyonel teşhis simülasyondur; toplama recommendation/ERRORS/4 değerlerini sabit kullanır. MCP topic doğrular ama içerikteki ilk satırları okur. Canlı olay verisi entegrasyonu veya servis hedefi çıkarımı yoktur.
- Yalnızca denetimli tek mock eylem vardır; gerçek iyileştirme veya doğrulanmış canlı model kalite/maliyet bütçesi yoktur. Karakter sınırı token penceresi garantisi değildir; anlamsal ve Activity tekrarları ücretli çağrıları yineleyebilir.
- Tek backend/worker, yerel HttpSession ve geliştirme Temporal SQLite servisi vardır; üretim TLS/MCP auth, HA, yedekleme/saklama/uzlaştırma veya uyumlu workflow dağıtım stratejisi yoktur.
- Bekleyen başlatma dispatch'i sessizce tükenebilir, manuel `/run` gerekebilir. Hata görünümü yazılamazsa veya yürütme süresi biterse veritabanı aşaması eski kalabilir. Uzlaştırıcı/alarm yoktur.
- Kanıt/sınıflandırma kalitesinin düşmesi tek başına mock restart önerisini engellemez; Java degraded sentezi veya desteklenmeyen hedefi reddeder. Atıf doğrulaması kimlikleri kontrol eder, doğruluğu değil.
- Redis kabulü arızada kapanır, ancak readiness yalnızca veritabanıdır. Redis yeniden başlatması sayaçları, backend yeniden başlatması süreç içi metrikleri sıfırlar.
- React mevcut inceleme kimliğini bileşen durumunda tutar; sayfa yenileme seçimi kaybettirir. Liste/geçmiş/kimlikle devam arayüzü yoktur. Karar teyidi hatalarında manuel durum yenileme gerekebilir; genel 503 yanıtı frontend yardımcısının beklediği InvestigationView içermeyebilir.
- Temizleme/injection savunmaları eksiksiz değildir; ilk soru/profil/temizlenmiş kanıt hassas olabilir. İzleme collector/dashboard/alarm veya eksiksiz inceleme başına token defteri yoktur.

Uzun süreli bellek ve çok ajanlı yürütme bilinçli kapsam dışıdır, hata değildir. Tarihsel temel mimari/plan bütünleşik uygulamadan önceki kapsamı anlatır; context-state.md Temporal'dan öncedir; orchestration.md içindeki “Temporal next” ifadesi tarihseldir. implementation-progress.md checkpoint bazında sayılar içerir; bunlar birden fazla güncel test toplamı değildir. Mevcut yapı için bu dokümanı, çalıştırılmış kontroller için tarihli doğrulama kayıtlarını kullanın.

## 26. Bilinçli Olarak Kapsam Dışında Tutulanlar

| Kapsam dışı | Neden |
| --- | --- |
| Çok ajanlı framework'ler | Bilinen sınırlı grafik Java ve Temporal ile yönetilir; delegasyon gereksinimi yoktur |
| Rastgele SQL/kabuk/sınırsız HTTP araçları | Model metni yetkiye veya sınırsız yürütmeye dönüşmemelidir |
| Kafka, mikroservisler, Kubernetes, Spring Cloud | Mevcut monorepo ve kalıcı görev kuyruğu ek dağıtık platform gerektirmez |
| Vektör veritabanı/uzun süreli bellek | İncelemeler arası retrieval/bellek için tanımlı gereksinim yoktur |
| Uygulama JWT'si/Redis oturumları | Aynı origin HttpSession/OIDC mevcut tarayıcı ihtiyacını karşılar; ayrı token/oturum platformu gereksizdir |
| Otonom yıkıcı eylemler | Sahip incelemesi ve yalnızca mock eylem güvenli operasyon sınırı sağlar |
| CQRS/event sourcing/DDD iskeletleri/genel eşleme framework'leri | Küçük özellik servisleri, repository'ler ve tipli record'lar mevcut sözleşmeleri karşılar |

## 27. Mimari Kararlar

| Karar | Mevcut seçim | Neden / ertelenen alternatif |
| --- | --- | --- |
| Depo / HTTP teknolojisi | Monorepo, bağımsız derlemeler, MVC/bloklayan JPA | Tanıdık transaction modeliyle tutarlı uygulama; ek depolar/reactive yapı gereksiz |
| Tarayıcı kimliği | Google OIDC + sunucu oturumu + aynı origin CSRF | Framework protokolü, dahili UUID sahipliği yönetir; JWT yaşam döngüsü ertelendi |
| Şema sahipliği | Flyway V1-V6, Hibernate validate | İncelenebilir evrim, otomatik şema değişimi yok |
| Dış kimlik | Benzersiz provider + subject, bağımsız UUID | E-posta değişebilir/paylaşılabilir; token veya e-posta kimliği yanlıştır |
| Kontrol | Tipli model önerileri, sabit Java yönlendirmeleri | Üretilmiş yürütme yetkisi olmadan anlamsal yardım |
| Dokümantasyon sınırı | Özel, salt okunur MCP | Sınırlı içerikle gerçek protokol/keşif; genel uzak yetenek erişimi yok |
| Kalıcılık | Temporal geçmişi + kısa veritabanı görünümü transaction'ları | Yeniden başlatmaya dayanıklı zamanlayıcı/onay; senkron yürütme geliştirme alternatifidir |
| Durum | PG görünümleri/denetim/eylem kaydı | Kalıcı, sahip tarafından okunabilir veriler ve idempotans; yürütme Temporal geçmişinde |
| Bağlam | Zorunlu kaynak öncelikli karakter/öğe/kaynak sınırları | Öngörülebilir kanıt yüzeyi; gereksiz vektör deposu veya uydurma token bütçesi yok |
| Redis | Yalnızca geçici kabul kontrolü | Başlatma/sağlayıcı yükünü sınırlama; gereksiz önbellek/kilit/oturum taşıması yok |
| Eylemler | Sahip onaylı sabit mock kaydı | Gerçek yan etkilerden önce onay/idempotans doğrulama |
| Modeller / altyapı | Çevrimdışı varsayılan, isteğe bağlı canlı adaptör; altı yerel servis | Dış kimlik bilgisi olmadan mekanizma testleri; canlı sağlayıcı değerlendirmesi/üretim dağıtımı ertelendi |

## 28. Gelecekteki Geliştirmeler İçin Kurallar

1. Mimari değişikliklerden/önemli çalışmalardan önce bu bağlamı okuyun, sonra gerçek uygulamayı ve ilgili testleri inceleyin. Uyuşmazlıkları düzeltin; dokümantasyon çalıştırılabilir davranıştan daha yetkili değildir.
2. Deterministik Java kontrolünü, tipli sınırları, sahiplik ve araç yetkilendirmesini, bağlam bütçelerini, Temporal determinizmini ve eylem idempotansını koruyun. LLM politikayı aşamaz.
3. Controller'ları ince tutun, dış/ağ çağrılarını veritabanı transaction'larından çıkarın. Şema Flyway'e aittir; uygulanmış migration'ları yeniden yazmak yerine yenisini ekleyin.
4. Deterministik olmayan/model/araç işleri Activity'lerde kalsın. Değişiklikleri replay uyumu, sınırlı denemeler ve idempotent görünüm/dış etkiler için tasarlayın; benzersiz veritabanı eylem kaydını uzakta tam bir kez yürütmeyle eşitlemeyin.
5. Altyapı, uzun süreli bellek veya çok ajanlı mimariyi yalnızca somut tanımlı gereksinimde ekleyin. Modelin ürettiği sınırsız SQL/HTTP/kabuk yürütmesi eklemeyin.
6. Gerçek PostgreSQL ve uygun servis/framework sınırlarıyla anlamlı davranış testleri ekleyin. Tamamlandı demeden ilgili testleri çalıştırın; başarısız/çalıştırılmayan/canlı kimlik bilgisi gerektiren kontrolleri dürüstçe bildirin.
7. Açık gereksinim değişmedikçe oturum/CSRF ve aynı origin akışını koruyun. Sırları commit etmeyin; sağlayıcı token'larını/ham claim'leri/gizli düşünce zincirini API, tablolar veya telemetriye çıkarmayın.
8. Simülasyonları ve kalite düşüşünü görünür tutun; daha katı politikayı ancak uygulayıp test ettikten sonra belgeleyin. Çevrimdışı test verisinden gerçek model kalitesi çıkarmayın.
9. Bakım Sözleşmesi'nin her kategorisinde bu dosyayı aynı değişiklik içinde güncelleyin; özel rehberleri tutarlı, geçmiş doğrulamaları tarihli tutun.

Destekleyici kaynaklar: [başlangıç/bütünleşik uygulama](capstone.md), [kimlik doğrulama kurulumu](authentication.md), [kalıcılık ayrıntıları](persistence.md), [Temporal ayrıntıları](temporal.md), [MCP ayrıntıları](mcp.md), [üretim AI kontrolleri](production-ai.md), [geçmiş ilerleme](implementation-progress.md), [geçmiş bütünleşik doğrulama](capstone-verification.md).

## 29. Yerel Codex geliştirme araçları (2026-10-08)

Windows kullanıcı ortamında RTK 0.50.0 (resmî winget paketi) ve Graphify `graphifyy` 0.9.80 (PyPI/uv izole araç ortamı) kuruldu. RTK'nin onaylanan global Codex PreToolUse hook'u ve Graphify'nin resmî kullanıcı skill'i geliştirme yardımcısıdır; uygulama bağımlılığı veya uygulamaya verilmiş yeni araç yetkisi değildir. Codex `config.toml` dosyasına yalnızca onaylanan RTK hook tanımının hash güven kaydı ve iki RTK SQLite veri dizini için dar sandbox yazma izinleri eklendi; diğer mevcut ayarlar, shell profilleri ve uygulama kaynakları korundu. Mevcut Codex multi_agent/hook özellikleri zaten etkin olduğundan ek özellik ayarı gerekmedi; yeni MCP sunucusu kaydedilmedi. Winget dizininin Windows sandbox çalıştırma kısıtı nedeniyle aynı doğrulanmış RTK binary'si kullanıcı `.local/bin` dizinine kopyalandı ve bu dizin kullanıcı PATH'inde öne alındı.

RTK desteklenen Git/Maven komutlarını özetler; backend dizininde `rtk mvn` Maven Wrapper'ı kullanır. Debug için ham komut, `rtk proxy` ve çıktıda belirtilen `rtk recall` kullanılabilir. Graphify yalnızca geniş yapısal analiz/etki soruları için, gerektiğinde yerel `--code-only --no-cluster` çıkarımı ve bütçeli sorgularla kullanılır. `graphify-out/` üretilmiş yerel indeks olup `.gitignore` ile dışlanır. Java metod çağrıları/sınıf referansları çıkarılır; bu sürümde kaynakları bulunmayan haricî Maven dependency uçları grafikten çıkarılır, Spring'in dinamik çalışma zamanı grafiği garanti edilmez. SQL grammar ek paketi kurulmadığından SQL migration analizi kapsam dışıdır.

Yeni çalıştırılan kontrol: JDK 25.0.2 ve Maven Wrapper 3.9.16 ile `rtk mvn -B '-Dtest=ContextTest,ClassificationTest' test` — 10 test, 0 failure/error, exit 0. Depo dışında oluşturulan tanılama örneğinde assertion, iç içe exception, kök neden ve kullanıcı stack frame'leri RTK çıktısında korundu; filtreli/ham Maven exit 1, hatalı Git ref exit 128 olarak eşleşti; `rtk recall` ham çıktıyı geri verdi. Graphify 107 kod dosyasından 1.012 düğüm/2.398 kenar üretti; sınıf, metod çağrısı ve sınıf bağımlılığı sorguları doğrulandı. Gerçek Codex CLI oturumunda otomatik `git status` → `rtk git status` çalıştırması exit 0 döndürdü ve sayaç 6'dan 7'ye arttı; skill/hook keşif kontrolleri hatasız, RTK hook güven durumu trusted idi. Yedi ölçümlü komutta 2.052 tahmini çıktı token'ı (%56,5) tasarruf kaydedildi. Bunlar tüm uygulama entegrasyon testlerinin yeniden çalıştırıldığı anlamına gelmez. Ayrıntılı kapsam ve Codex doğrulaması [CODEX_TOOL_SETUP.md](CODEX_TOOL_SETUP.md) dosyasındadır.
