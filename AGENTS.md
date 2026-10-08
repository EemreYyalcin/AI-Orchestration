# Depo çalışma kuralları

- Önemli bir çalışmaya başlamadan önce [docs/PROJECT_CONTEXT.md](docs/PROJECT_CONTEXT.md) dosyasını okuyun.
- Çalıştırılabilir kodu, yapılandırmayı, migration dosyalarını ve testleri inceleyin; bunlar dokümantasyondan daha yetkilidir. Çalışan kodu eski dokümana uydurmak yerine dokümanı düzeltin.
- Java'nın deterministik kontrolünü, tipli sınırları, sahiplik ve araç yetkilendirmesini, sınırlı bağlamı, Temporal determinizmini ve eylemlerin idempotansını koruyun.
- Mimari, çalışma davranışı, bağımlılıklar, sözleşmeler, yapılandırma, testler veya kısıtlar anlamlı biçimde değiştiğinde PROJECT_CONTEXT.md dosyasını aynı değişiklik içinde güncelleyin; Bakım Sözleşmesi'ne uyun.
- Davranış değişikliklerini tamamlanmış saymadan önce ilgili testleri çalıştırın. Yeni çalıştırılan kontrolleri geçmiş doğrulama sonuçlarından ayırın.
- Altyapı veya yetenekleri yalnızca somut bir gereksinim için ekleyin. Modelin ürettiği sınırsız SQL, HTTP veya kabuk komutlarının çalıştırılmasına izin vermeyin.

## Yerel analiz araçları

- RTK kuruluysa desteklenen Git/Maven/test komutlarında kullanın; Maven modül dizininde `rtk mvn` mevcut wrapper'ı seçer. Desteklenmeyen komutları normal çalıştırın; `mvnw.cmd` otomatik dönüşümünü varsaymayın. Hata incelemesinde `rtk recall <hash>`, doğrudan komut veya `rtk proxy <komut>` ile ham çıktıyı okuyun; yalnızca özetten testlerin geçtiği sonucunu çıkarmayın.
- Graphify'yi geniş dependency/call graph ve değişiklik etkisi sorularında kullanın; basit okuma/düzenlemelerde graph analizi başlatmayın. Mevcut `graphify-out/graph.json` için `graphify query "<soru>" --budget 1000`, `explain`, `path` ve `affected` kullanın; belirsiz sınıf/metod adlarında tam düğüm kimliğini seçin.
- Graphify indeksi gerektiğinde `graphify extract . --code-only --no-cluster --max-workers 2` ile yerelde yenilenir; kaynakları değiştirmeyin, üretilen `graphify-out/` dosyalarını Git'e eklemeyin. Semantik/API analizi veya otomatik izleme başlatmayın. AST ilişkilerini kaynak kodla doğrulayın; haricî Maven paketlerinin kenarları bu sürümde grafikten çıkarılır.
- Kurulum, kullanım ve tarihli doğrulama ayrıntıları: [docs/CODEX_TOOL_SETUP.md](docs/CODEX_TOOL_SETUP.md).
