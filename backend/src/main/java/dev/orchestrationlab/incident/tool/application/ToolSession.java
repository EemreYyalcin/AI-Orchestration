package dev.orchestrationlab.incident.tool.application;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Tek akıl yürütme çağrısının güvenilir ToolContext ve araç çağrı sayacını taşır. Model izin kümesini
 * genişletemez; atomik sayaç eşzamanlı çağrılarda da sekiz çağrı sınırını korur.
 */
public final class ToolSession {
    private final ToolExecutionService execution;
    private final ToolContext context;
    private final AtomicInteger calls = new AtomicInteger();
    /**
     * Tek akıl yürütme çağrısının yürütme servisi ve güvenilir bağlamını bağlar; sayaç her yeni
     * oturumda sıfırdan başlar.
     */
    public ToolSession(ToolExecutionService execution, ToolContext context) {
        this.execution = execution;
        this.context = context;
    }
    /**
     * Çağrı sayısını artırır; sekizi aşan isteği reddeder. Geçen çağrıyı mevcut sahiplik/izin
     * bağlamıyla ToolExecutionService’e aktarır; başarısız araç isteği de bütçeyi tüketir.
     */
    public ToolResult call(ToolKind kind, ToolInput input) {
        if (calls.incrementAndGet() > 8) throw new IllegalStateException("Tool call budget exceeded");
        return execution.execute(context, kind, input);
    }
    /**
     * Araç türünün Java’dan gelen izin kümesinde olup olmadığını bildirir; callback’lerin modele
     * sunulmasını filtrelemek için kullanılır.
     */
    public boolean allows(ToolKind kind) { return context.allowedTools().contains(kind); }
    /**
     * Bu oturumdaki denenen araç çağrı sayısını döndürür; dış servis başarısıyla aynı sayı olmak
     * zorunda değildir.
     */
    public int callCount() { return calls.get(); }
}
