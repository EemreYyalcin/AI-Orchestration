# Tool calling — Checkpoint 7

> This records the bounded tool-calling demonstration. The current capstone uses
> separate classification and synthesis calls with Java-directed collection;
> documentation can now use real MCP transport. See [PROJECT_CONTEXT.md](PROJECT_CONTEXT.md)
> for current integration and retry policies. Version-selection claims below are dated.

Versions checked against official documentation on 2026-10-07: Spring AI 2.0.1
is the latest stable compatible 2.0.x release for Boot 4.1.1. Its BOM manages
`spring-ai-starter-model-openai`. No snapshots/milestones or provider SDK directly
added. Sources: [compatibility/BOM](https://docs.spring.io/spring-ai/reference/getting-started.html),
[OpenAI starter/configuration](https://docs.spring.io/spring-ai/reference/api/chat/openai-chat.html),
[current tool execution](https://docs.spring.io/spring-ai/reference/api/tools.html).

```text
Prompt + schemas → model → tool name + typed JSON arguments
→ Spring tool manager parses → Java tool policy and dispatch
→ bounded typed evidence/failure → conversation → next model turn → answer
```

The LLM does not execute the Java method. The application executes it. Four
model-visible methods delegate to ToolSession → ToolExecutionService →
EvidenceAdapter → explicitly simulated adapters. No tool accepts SQL, arbitrary
URLs, shell commands or Java class names. Topics are enums, services allowlisted,
items limited to 10, each item to 1000 characters. ToolContext is created by
Java, never supplied in tool arguments. Ownership and the allowed tool set are
checked before dispatch. Per-call budgets: eight tool calls and four model
turns. A bounded four-worker executor with a 32-task queue enforces tool timeouts;
failure is represented explicitly without exposing integration exceptions.
Adapters must also respect network timeouts; cancellation alone cannot terminate
an arbitrary uncooperative third-party call.

Spring AI 2.0 moved default looping into the tool advisor. Our adapter opts out
per request using the documented AdvisorParams and executes each bounded turn
through ToolCallingManager. Controllers depend on no Spring AI types. Only
approved callbacks are exposed; tool-resolution fallback is not enabled.

The AI profile enables OpenAI with OPENAI_API_KEY and explicit AI_CHAT_MODEL.
Without it all model auto-configurations are disabled and a clearly labelled
deterministic simulation is used. Neither Codex's model identity nor invented
credentials configure the API. Provider timeout is 20 seconds, SDK retries zero,
Spring retries one attempt; broader workflow retry policy is added later.
Automated tests use fakes and Spring's schema/callback infrastructure without
provider credentials. No investigation execution endpoint is introduced here.
