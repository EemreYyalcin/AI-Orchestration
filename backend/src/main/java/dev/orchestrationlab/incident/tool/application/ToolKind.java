package dev.orchestrationlab.incident.tool.application;

/**
 * Java’nın tanıdığı dört salt okunur kanıt yeteneği: LOGS log, DATABASE sabit veritabanı teşhisi,
 * DOCUMENTATION runbook, METRICS metrik. Bu enum rastgele handler adı veya yürütme komutu değildir.
 */
public enum ToolKind { LOGS, DATABASE, DOCUMENTATION, METRICS }
