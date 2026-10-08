# Structured classification and routing — Checkpoint 8

> Specialized routing guide. [PROJECT_CONTEXT.md](PROJECT_CONTEXT.md) is the
> canonical current architecture and documents the actual records, route table
> and durable execution around these mechanisms.

IncidentClassification contains enum incidentType, enum severity, allowlisted
enum tools and a bounded summary. Record validation rejects missing, empty and
oversized values. Spring AI 2.0.1 entity output uses one explicit
StructuredOutputValidationAdvisor to validate its generated schema. We do not
also enable the automatic validateSchema advisor: tests caught multiplied
retries when both were combined.
One framework correction attempt plus two ClassificationService attempts bounds
classification calls to four. Exhaustion returns an explicit degraded UNKNOWN
classification, not fabricated successful analysis.

Provider-native output is opt-in via app.ai.native-structured-output=true only
after verifying the configured model supports it. Local validation remains.
Sources: [schema validation](https://docs.spring.io/spring-ai/reference/api/structured-output/validation.html),
[native output](https://docs.spring.io/spring-ai/reference/api/structured-output/native.html).

Java WorkflowRoutingPolicy maps types to approved sources: DATABASE requires
database evidence, PERFORMANCE metrics, other types logs. Documentation is
optional. Model tool suggestions outside the type's policy are rejected and
retry/fallback applies. Java adds required evidence; model output never becomes
class names, URLs, SQL or workflow commands. Classification executes no action.
Offline heuristics are labelled SIMULATED and are for deterministic tests/demo,
not proof of live-model quality.
