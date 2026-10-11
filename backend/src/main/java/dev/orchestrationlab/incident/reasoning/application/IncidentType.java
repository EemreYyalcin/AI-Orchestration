package dev.orchestrationlab.incident.reasoning.application;
/**
 * İzinli olay türleri: DATABASE bağlantı/veritabanı, APPLICATION uygulama hatası, DEPENDENCY dış
 * bağımlılık, PERFORMANCE performans, UNKNOWN belirsiz olay. WorkflowRoutingPolicy bu enum üzerinden
 * sabit kaynak politikasını seçer.
 */
public enum IncidentType { DATABASE, APPLICATION, DEPENDENCY, PERFORMANCE, UNKNOWN }
