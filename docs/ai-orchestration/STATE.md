# AI Incident Orchestrator — çalışma durumu

**Son inceleme: 2026-10-08, Europe/Istanbul.** Bu kayıt ilk sistem envanteri ve dokümantasyon görevinin sonucudur. Ayrıntılı mimari [PROJECT_CONTEXT.md](../PROJECT_CONTEXT.md), kısa harita [ARCHITECTURE.md](ARCHITECTURE.md), eğitim içeriği alanı [LEARNING_GUIDE.md](LEARNING_GUIDE.md) içindedir.

## Tamamlanan kapsam

- Mevcut AGENTS.md korundu; gerçek entegrasyon, davranış koruma, production veri sınırı ve doküman bakımı yönergeleri eklendi.
- Spring bileşenleri, POM'lar, profil yapılandırmaları, Flyway V1–V6, araç/model/workflow kodu, testler, frontend ve Docker tanımları incelendi.
- Mevcut yerel Graphify indeksi bütçeli sorgularla yardımcı kaynak olarak kullanıldı; sonuçlar kaynak kodla karşılaştırıldı. Yeni indeks/servis/bağımlılık kurulmadı.
- Uygulama davranışı, kod, migration, yapılandırma veya çalışan servisler değiştirilmedi. Production verilerine yazılmadı; Compose build/up/down/restart ve volume işlemleri yapılmadı.
- Eğitim yol haritasının asıl içeriği bu görevde verilmedi. İçerik veya tamamlanma kaydı uydurulmadı.

## Bu görevde yeni çalıştırılan kontroller

| Kontrol | Sonuç ve kanıt kapsamı |
| --- | --- |
| `docker compose config --services` | Compose çözümlendi; altı servis: frontend, backend, documentation-mcp-server, postgres, redis, temporal. Tam environment dökümü alınmadı. |
| `docker compose ps --all --format '{{.Service}} {{.State}} {{.Health}}'` | Altı servisin tamamı running / healthy. Bu, uçtan uca iş akışı doğrulaması değildir. |
| Backend içinde `/actuator/health` ve `/actuator/health/readiness` | HTTP başarılı, UP. Readiness yalnızca DB içerir; bütün araçların hazır olduğunu kanıtlamaz. |
| Backend → `documentation-mcp-server:8081/actuator/health` | Özel ağ üzerinden HTTP başarılı, UP. Canlı stack'te MCP tool çağrısı yapılmadı. |
| MCP `/mcp`, tarayıcı Origin header'ı | HTTP 403; mevcut Origin filtresi çalışıyor. |
| `temporal operator cluster health --address 127.0.0.1:7233` (Temporal container'ında) | SERVING; canlı workflow çalıştırma/replay testi değildir. |
| nginx üzerinden `/`, `/api/health`, `/api/me` | Sırasıyla HTTP 200, 200, 401. Frontend/proxy ve kimliksiz korumalı API sınırı doğrulandı. |
| Backend etkin profil / kimlik bilgisi varlık kontrolü | `container,temporal,mcp`. OPENAI_API_KEY, GOOGLE_CLIENT_ID ve GOOGLE_CLIENT_SECRET boş/tanımsız; yalnızca varlık bilgisi kontrol edildi, değerler yazdırılmadı. |
| Backend `rtk mvn -B verify`, süreçte JDK 25.0.2 | BUILD SUCCESS, **83 test**, 0 failure/error/skipped. Yeni Surefire XML: 17 suite, aynı sayılar. Ayrı PG/Redis Testcontainers ve Temporal test ortamı; çalışan Compose verisi hedeflenmedi. |
| MCP `rtk mvn -B -f ../mcp/documentation-server/pom.xml verify` (backend dizininde, JDK 25.0.2) | BUILD SUCCESS, **2 test**, 0 failure/error/skipped; yeni Surefire XML ile doğrulandı. Testte gerçek Streamable HTTP initialize/keşif/çağrı ve girdi sınırları var. |
| Frontend `node node_modules/typescript/bin/tsc --noEmit`, ardından `node node_modules/vite/bin/vite.js build` | Node 24.19.0; tip kontrolü ve üretim derlemesi başarılı. Mevcut package.json build adımları doğrudan çalıştırıldı; bu shell'de npm PATH'te yoktu, bağımlılıklar yeniden kurulmadı. Tarayıcı E2E testi değildir. |
| Git/doküman kontrolü | `rtk git diff --check` başarılı; beş dokümanın yerel Markdown hedefleri mevcut, üç yeni dosyada trailing whitespace yok. Git durumunda yalnızca AGENTS.md, PROJECT_CONTEXT.md ve üç yeni rehber var. |

Başlangıçtaki backend `rtk mvn -B verify` denemesi PATH Java 23 ile class-file 69 / runtime 67 uyuşmazlığı verdi; **sıfır test** çalıştı. Ham RTK çıktısı incelendi; mevcut JDK 25 yalnızca sonraki süreçlerde seçilerek düzeltildi. MCP modülünde doğrudan `rtk mvn` wrapper/global Maven bulamadı; backend wrapper'ı ve `-f` ile yeniden çalıştırıldı. Global Java/PATH veya proje build ayarları değiştirilmedi.

Yeni başarılı toplam **85 test çalıştırmasıdır**; üç isteğe bağlı smoke buna dahil değildir. Testlerin kullandığı mevcut fixture/mock'lar gerçek Google/OpenAI bağlantısını kanıtlamaz; bu görevde yeni sahte veri veya stub servisi eklenmedi.

## Geçmiş doğrulamalar

[2026-10-07 capstone kaydı](../capstone-verification.md): backend 83 + MCP 2 + üç açık smoke = 88 başarılı test çalıştırması; frontend build, altı servis, temiz migration ve canlı yerel worker/backend restart doğrulaması. Bu geçmiş çalıştırmadır; yeni test toplamına eklenmez. [2026-10-08 araç kurulumu](../CODEX_TOOL_SETUP.md) kaydındaki 10 seçili test de ayrı tarihli kontroldür.

`ContainerStackSmoke` veritabanına fixture yazar/siler ve backend container'ını yeniden başlatır; bu görevde seçilmedi. `TemporalRestartSmoke` ve `McpInvestigationSmoke` da çalıştırılmadı. Canlı OpenAI, Google girişi veya production sistem testi iddiası yoktur.

## Doğrulanmış mevcut durum

- Çalışan sistem gerçek Java/Spring Boot + React monorepo'sudur; PostgreSQL/Flyway, Redis kabul kontrolü, Temporal ve özel MCP modülü zaten mevcuttur.
- Kontrol Java'dadır; Temporal deterministik sırayı ve kalıcı onayı yürütür. Spring AI model adaptörü tipli sınıflandırma/sentez sağlar; etkin container varsayılan çevrimdışı modeli kullanır.
- Sahiplik, CSRF, tipli izin listeleri, bağlam sınırları ve idempotent mock eylem kaydı mevcut güvenlik sınırlarıdır.
- Operasyonel LOGS/DATABASE/METRICS çıktıları simülasyondur. DATABASE aracı gerçek uygulama PG'sini teşhis etmez. MCP gerçek protokol kullanır, içerik iki sabit runbook'tur. Gerçek remediation yoktur.
- Servis hedefi toplamada sabit `recommendation` kalır; modelin olay türünü seçmesi gerçek hedef keşfi sağlamaz.

## Belirsizlikler ve erişim eksikleri

1. Gerçek teşhis veri kaynağının sistemi/endpoint'i, sahipliği, servis eşlemesi, salt okunur yetkileri ve veri hassasiyeti verilmedi. Bu nedenle canlı operasyonel entegrasyon uygulanamaz veya doğrulanamaz.
2. Çalışan backend'de OpenAI/Google kimlik bilgileri yok. Host'ta başka konumlarda erişim bulunup bulunmadığı araştırılmadı; canlı model kalitesi, maliyeti ve gerçek kullanıcı girişi bilinmiyor.
3. Container image'larının mevcut HEAD ile birebir kaynak eşliği bu görevde doğrulanmadı. Kaynak/test bulguları ve çalışan container sağlık bulguları ayrı kanıtlardır.
4. Canlı veritabanındaki bütün kayıtlar, migration geçmişi, workflow geçmişleri ve uçtan uca inceleme yürütmesi bu görevde sorgulanmadı. Migration/test doğrulaması mevcut kaynak ve ayrı test ortamı içindir.
5. Production ortamı, TLS/auth, HA, backup/restore, saklama politikası ve collector/alarm hedefi belirtilmedi. Mevcut Compose yerel geliştirme dağıtımıdır.
6. Eğitim yol haritası paylaşılmadı; eski checkpoint belgelerinden kullanıcıya ait yeni eğitim planı türetilmedi.

## Sıradaki görev — öneri, henüz uygulanmadı

**Tek bir mevcut teşhis aracını gerçek, salt okunur bir veri kaynağına bağlamak.** Önce kaynak sahibi, hedef servisler, erişim yöntemi ve dar okuma yetkisi netleştirilmeli; `EvidenceCollector` içindeki sabit hedef yerine Java tarafından doğrulanan servis hedefi sözleşmesi tasarlanmalıdır. Kaynak belli olmadan yeni servis veya genel amaçlı HTTP/SQL aracı eklenmez.

Kabul ölçütleri: mevcut ToolContext/sahiplik/izin listesi korunur; girdiler tipli ve sınırlandırılmıştır; ağ işi Activity sınırında ve DB transaction'ı dışındadır; süre/çıktı/maskeleme kuralları uygulanır; kaynak hatası ve veri provenansı açıkça raporlanır; mevcut `diagnosticSourcesSimulated` alanının karma gerçek/simüle kaynakları doğru temsil etmesi değerlendirilir. Gerçek salt okunur bağlantı ve hata/yetkisiz hedef davranışları anlamlı testlerle doğrulanır. Eğitim rehberi ancak kullanıcı yol haritasını ilettiğinde doldurulur.

Sonraki çalışmalar bu kaydı tarihli sonuçlarla güncellemeli; öneri, kaynak incelemesi, yeni çalıştırılan kontrol ve geçmiş sonuçları ayrı tutmalıdır.
