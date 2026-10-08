# Codex — RTK ve Graphify kurulumu

Kontrol tarihi: **2026-10-08**, Windows 11 Pro x64 (10.0.26200), PowerShell 7.6.5. Kurulum/testler gerçekten çalıştırıldı; geçmiş uygulama test sonuçları bu rapora dahil edilmedi.

| Bileşen | Sürüm | Durum | Codex entegrasyonu |
| --- | --- | --- | --- |
| RTK | 0.50.0 | Kurulu; Git/Maven ve hata tanılaması doğrulandı | Global PreToolUse hook'u; yalnızca tam tanımın hash'i güvenilir; AGENTS.md/RTK.md |
| Graphify (`graphifyy`) | 0.9.80 | Kurulu; Java AST/sınıf/metod/çağrı/sınıf dependency sorguları doğrulandı | Resmî `graphify install --platform codex`; kullanıcı skill'i etkin ve Codex tarafından keşfedildi |
| uv | 0.12.23 | Graphify için kullanıcı kapsamında kuruldu | İzole Python araç ortamı; yeni MCP kaydı yok |

## Önce / sonra

| Kontrol | Önce | Sonra |
| --- | --- | --- |
| Codex CLI | 0.160.1 | Aynı sürüm; gerçek `codex exec` ve app-server kontrolleri çalıştı |
| winget | 1.29.380 | Aynı sürüm |
| Git | 2.53.0.windows.3, Codex'in mevcut runtime'ında | Aynı kurulum |
| Python | `python` PATH girdisi Microsoft Store alias'ıydı; çalışmıyordu. Codex runtime'ında Python 3.12.14 vardı | Mevcut 3.12.14 kullanıldı; sistem Python'u değiştirilmedi |
| uv / RTK / Graphify | PATH'te bulunmadı; RTK/uv için winget kurulum kaydı yoktu | Yukarıdaki sürümler kuruldu |
| Java | PATH Java 23; ayrıca `~/.jdks/openjdk-25.0.2` mevcuttu | Testler süreç içi `JAVA_HOME` ile mevcut JDK 25.0.2'de çalıştı; global Java seçimi değişmedi |
| Maven | Global `mvn` yok; backend wrapper 3.9.16 mevcut | `rtk mvn` backend dizininde mevcut `mvnw.cmd` dosyasını seçti |
| Codex config | Mevcut model, notify, plugin/MCP ve trusted project ayarları | Korundu; yalnızca RTK hook güven kaydı ve onaylanan RTK veri dizinleri eklendi |
| Git | Depo içeriği zaten untracked, henüz commit yok | Commit/stage/reset yapılmadı; 115 mevcut kaynak dosyasının SHA-256 değeri aynı |

## Kaynak ve paket doğrulaması

- [RTK resmî depo/Windows kurulumu](https://github.com/rtk-ai/rtk): `winget install --id rtk-ai.rtk --exact --version v0.50.0 --source winget --scope user`. Winget resmî GitHub release arşivinin SHA-256 değerini doğruladı: `cb03399305135dd59ee23eb59a3260ccdeea5a8e08fbc7a271b115b85583a6c9`.
- [uv resmî Windows kurulumu](https://docs.astral.sh/uv/getting-started/installation/): winget kullanıcı kapsamı, sürüm 0.12.23; arşiv SHA-256 `75d05de6762778c31ee183398de7dd15093fad0ed90b1f236d8205ea5ec00c90` doğrulandı. Gereksiz/yönetici gerektiren bağımlılık kurulumu atlandı; mevcut ortamda uv çalıştı.
- [Graphify-Labs/graphify](https://github.com/Graphify-Labs/graphify) ve [Toessi/Graphify](https://github.com/Toessi/Graphify) incelendi. Her ikisinde Java desteği bulunuyor; PyPI'nin resmî `graphifyy` paketi **Graphify-Labs** deposunu işaret ediyor. Seçilen resmî depo 2026-10-07'de, Toessi deposu 2026-09-11'de güncellenmişti; hiçbiri archived değildi.
- [PyPI graphifyy 0.9.80](https://pypi.org/project/graphifyy/0.9.80/) metadata'sı, Python gereksinimi (`>=3.10`) ve repository bağlantısı kontrol edildi. `uv tool install graphifyy==0.9.80 --python <mevcut-Codex-Python> --index-url https://pypi.org/simple` çalıştırıldı. Resmî wheel ayrıca indirilip PyPI SHA-256 değeriyle karşılaştırıldı: `448dbc36edf6d17064b64477bb4dafe017d10c86ff1674c7a2c0e1eda61c2b82`, eşleşti. `tree-sitter-java` 0.23.5 temel paketle geldi.

## Değişen dosyalar ve ortam

- `C:\Users\Emre\.codex\hooks.json`: resmî `rtk init -g --codex` ile `rtk hook codex` kaydı.
- `C:\Users\Emre\.codex\RTK.md` ve `C:\Users\Emre\.codex\AGENTS.md`: resmî RTK talimatı ve referansı; önce bu global dosyalar yoktu.
- `C:\Users\Emre\.codex\config.toml`: yalnızca `[hooks.state.'C:\Users\Emre\.codex\hooks.json:pre_tool_use:0:0']` güven kaydı ve `[sandbox_workspace_write].writable_roots` eklendi. Güvenilir hash: `sha256:be6c6fcb2978ea348dd05101efed0afcf00efcbfb5d7e78949108142c875a77d`. Hook tanımı değişirse Codex yeniden güven incelemesi yapar.
- Sandbox yazma izni yalnızca kullanıcı tarafından onaylanan `C:\Users\Emre\AppData\Local\rtk` ve `C:\Users\Emre\AppData\Local\Packages\OpenAI.Codex_2p2nqsd0c76g0\LocalCache\Local\rtk` dizinlerine verildi. Sandbox modu/approval politikası değiştirilmedi.
- `C:\Users\Emre\.codex\skills\graphify\SKILL.md` ve `references/`: resmî Codex skill kurulumu. Skill dosyaları değiştirilmedi. `skills/list` cevabı `enabled: true`, `scope: user`, sıfır keşif hatası döndürdü. Mevcut `multi_agent` zaten true olduğundan yeni agent/özellik ayarı gerekmedi; analizde subagent kullanılmadı.
- Kullanıcı PATH'ine winget RTK/uv konumları ve `C:\Users\Emre\.local\bin` eklendi; `.local\bin` kullanıcı PATH'inin başına alındı. PowerShell profile, execution policy ve makine PATH'i değiştirilmedi.
- Winget RTK binary'sinin dizin ACL'leri Codex sandbox kullanıcısına çalıştırma izni vermedi. Aynı doğrulanmış binary `C:\Users\Emre\.local\bin\rtk.exe` konumuna kopyalandı; iki binary SHA-256 değeri eşit: `57c9b9723388e9f421bd1f82aabf1bf093ea3012b72ebbbe0ea855d739f1d622`. Bu konum mevcut araç dizininin izinlerini miras alır; sandbox'ın genel izinleri genişletilmedi.
- Projede `AGENTS.md` sonuna kısa kullanım sınırları eklendi; `.gitignore` içine `**/graphify-out/` eklendi; `docs/PROJECT_CONTEXT.md` ve bu rapor güncellendi. Uygulama kodu/pom/migration/testler değiştirilmedi.
- `graphify-out/` yaklaşık 4,35 MB yerel graph/cache oluşturdu; `git check-ignore graphify-out/graph.json` doğrulandı. Hiçbir indeks Git'e eklenmedi.

Ön durum kopyaları ve gerçek kontrol çıktıları `C:\Users\Emre\.codex\tool-setup-backup-20261008\` altında tutuluyor. Buradaki `config.toml.before`, `project-AGENTS.md.before`, `gitignore.before`, `PROJECT_CONTEXT.md.before`, PATH görüntüleri ve kaynak hash'leri karşılaştırma/geri alma içindir. Global config'i geri alırken bu çalışmadan sonra yapılan değişiklikleri koruyun.

## Kullanım

RTK hook'u desteklenen basit shell komutlarını otomatik dönüştürür. Hook'un kapsamadığı komutlar normal devam eder. PowerShell batch komutlarının ve `./mvnw.cmd test` ifadesinin otomatik dönüştüğünü varsaymayın; modül dizininde açık `rtk mvn` kullanın:

```powershell
rtk git status
rtk git diff
rtk git log -5
$env:JAVA_HOME = 'C:\Users\Emre\.jdks\openjdk-25.0.2'
Set-Location backend
rtk mvn -B '-Dtest=ContextTest,ClassificationTest' test
```

Tanılama için `rtk recall <çıktıda-gösterilen-hash>` veya `rtk proxy git ...` / `rtk proxy .\mvnw.cmd ...` kullanın. Maven `-X`/`-e` gerektiğinde ham proxy/doğrudan wrapper ile çalıştırılmalıdır. RTK framework stack frame'lerini/gürültüyü azaltabilir; tam trace için ham çıktı esas alınır. Genel `rtk err/test/summary` filtrelerini kritik hata araştırmasına otomatik olarak eklemeyin. Hook tarafından yeniden yazılmış komutlar Codex'in kendi izin/sandbox kontrollerinden geçmeye devam eder; RTK native sınıflandırıcısının tüm iç komutları tanıdığını varsaymayın.

Graphify Codex içinde **`$graphify`** ile seçilebilir. Örneğin: `$graphify mevcut grafikte EvidenceCollector.collect metodunu kimlerin çağırdığını incele; indeksi yeniden oluşturma.` Basit düzenlemelerde tam pipeline başlatmayın. PowerShell'de `/graphify` yerine CLI komutlarını kullanın:

```powershell
graphify query 'IncidentOrchestrator' --budget 1000
graphify explain 'IncidentOrchestrator'
graphify query 'EvidenceCollector collect' --context call --budget 1000
# Kod değiştiğinde, ihtiyaç varsa yerel AST indeksini yenile:
graphify extract . --code-only --no-cluster --max-workers 2
```

`path`/`affected` sorgularında sınıf ve constructor aynı adı paylaşıyorsa tam düğüm ID'sini kullanın. Graphify bu kurulumda API/semantik analiz, watch, git hook veya MCP sunucusu başlatmaz. Resmî `graphify codex install` ek komutu bu sürümde bir no-op hook ve her soruda query-first talimatı eklediği için uygulanmadı; resmî skill kurulumu ve mevcut AGENTS.md'ye kısa, görev kapsamlı yönergeler yeterlidir.

## Yeni çalıştırılan doğrulamalar ve sınırlar

| Kontrol | Sonuç |
| --- | --- |
| `rtk --version`, `graphify --help`, uv | Sürümler/CLI doğrulandı; Graphify sürümü kurulu dağıtım metadata'sından kontrol edildi |
| `rtk verify` | 154/154 paket filtre testi geçti. Ayrıca yazdığı "SKIP RTK hook not installed" satırı varsayılan Claude hook kontrolüne aittir; Codex için `hooks/list` ve gerçek CLI smoke testleri kullanıldı |
| Backend Maven | `ContextTest` 4 + `ClassificationTest` 6 = 10 test; 0 failure/error/skipped, exit 0; Surefire XML sonuçlarıyla karşılaştırıldı |
| Kontrollü hata örneği | Depo dışındaki küçük Maven/JUnit örneğinde 3 test: 1 pass, 1 assertion failure, 1 nested exception. RTK/ham exit 1; assertion expected/actual, exception, `Caused by`, root cause ve kullanıcı stack frame'leri korundu |
| Ham çıktı geri alma | `rtk recall 8d6875ed5725` exit 0; tam Maven çıktısı geri alındı. Ham fixture proxy çalıştırması da exit 1 |
| Git hata çıkış kodu | Olmayan ref: filtreli/ham exit 128 ve fatal tanılama eşleşti |
| Desteklenmeyen komutlar | Hook `Get-Date`, `java -version`, `mvnw.cmd` ve yönlendirmeli komutlar için boş cevap + exit 0 döndürdü; orijinal komut korunur. Gerçek Codex `Get-Date` exit 0 |
| Java Graphify | 107 kod dosyası; 1.012 node/2.398 edge. 91 Java dosyasından 602 Java kaynaklı node; tüm grafikte 422 `calls`, 702 `references`, 346 `method` kenarı |
| Sınıf/dependency | `IncidentOrchestrator --references [EXTRACTED]--> ContextBuilder`, 1 hop; sınıfın 16 bağlantısı `explain` ile bulundu |
| Metod/caller | `EvidenceCollector.collect` için `IncidentOrchestrator.run` L30 ve `InvestigationActivitiesImpl.collect` L35 çağrıları `affected --relation calls --depth 1` ile bulundu |
| Codex skill ve normal çalışma | App-server initialize/skills-list/hooks-list başarılı, hook `trusted`, keşif hataları yok. Gerçek `codex exec` mevcut graph'ı sorguladı, exit 0; hook güveni ve binary konumu düzeltildikten sonra `git status` → `rtk git status` dönüşümü gerçek çalıştırma kaydında görüldü, exit 0. Onaylanan veri dizini izinleriyle son Codex kontrolünde sayaç 6'dan 7'ye arttı; `rtk gain` exit 0 |
| Kaynak koruma | 115 kaynak dosyası hash karşılaştırmasında değişen dosya sayısı 0; Git stage/commit yapılmadı |

Sınırlamalar: bu Graphify sürümü Maven manifest düğümlerini üretse de projede tanımlanmayan haricî paket uçlarına ait `depends_on` kenarlarını düşürür. Haricî/transitif Maven dependency sorgularında `pom.xml` ve gerektiğinde Maven dependency çıktısı doğruluk kaynağıdır. SQL için isteğe bağlı `tree-sitter-sql` kurulmadı; altı migration SQL dosyası grafiğe katkı vermedi. Spring proxy/DI, reflection, overload çözümü ve çalışma zamanı çağrılarının eksiksiz temsil edildiği iddia edilmez. Sorgu bütçesi nedeniyle kesilen sonuçlarda Graphify açık truncation uyarısı gösterir; daraltın veya bütçeyi artırın. Tüm uygulama/infrastructure test paketi yeniden çalıştırılmadı.

## Token tasarrufu ve bakım

```powershell
rtk gain -p
rtk gain --history
rtk gain --daily
rtk gain --format json
rtk gain --recalls
```

Kurulumun son yedi ölçümlü komutunda RTK 3.630 → 1.578 tahmini çıktı token'ı, **2.052 token / %56,5** azalma kaydetti. Backend'in 10 testlik çalışması için ölçüm **%93,9** idi. Ham proxy çalışması sıfır tasarrufla kaydedildi. Son gerçek Codex komutu sayaçta ve istatistikte görüldü. Bunlar RTK'nin çıktı uzunluğundan hesapladığı tahminlerdir; Codex toplam kullanım/faturalandırma veya her gelecekteki komut için tasarruf garantisi değildir. Windows paketli desktop ve normal CLI veri yolları farklı olabilir; rapor alırken komutları çalıştırdığınız ortamın `rtk gain` çıktısını kullanın.

Yeni PATH/skill/global hook ayarları için açık terminal ve Codex'i yeniden başlatın. Güven kaydı bu kurulumda eklendiği için mevcut tanımı yeniden onaylama gerekmiyor; tanım değişirse Codex `/hooks` incelemesi ister. Normal RTK güncellemesi sonrası winget binary'si ile `.local\bin\rtk.exe` kopyasının sürüm/hash eşitliğini yeniden doğrulayıp kopyayı güncelleyin. Graphify izole ortamı mevcut Codex Python runtime'ını kullanır; o runtime kaldırılırsa uv üzerinden mevcut Python ile yeniden kurun. Güncellemeler bu görev kapsamında otomatikleştirilmedi.
