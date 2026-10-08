# Documentation MCP boundary — Checkpoint 9

> Specialized protocol/setup guide; [PROJECT_CONTEXT.md](PROJECT_CONTEXT.md) is
> the canonical current architecture. The capstone collector calls the tool
> execution service directly, and the full-investigation MCP smoke is now recorded
> in [capstone verification](capstone-verification.md). Lazy application connection
> does not remove Compose's explicit healthy-MCP startup dependency.

REST is an application-specific network API. Tool calling is the interaction
where a model requests a supplied capability and Java executes it. MCP is a
standardized discovery/communication protocol for tools, resources and prompts.
Orchestration coordinates the larger investigation, policy and durable state.
**MCP is not orchestration.**

```text
Orchestrator → Spring AI tool → Java policy/execution → MCP documentation adapter
→ SDK client → Streamable HTTP /mcp → documentation server → packaged runbooks
```

Current official docs checked before adding dependencies:
[Spring AI server](https://docs.spring.io/spring-ai/reference/api/mcp/mcp-server-boot-starter-docs.html),
[client](https://docs.spring.io/spring-ai/reference/api/mcp/mcp-client-boot-starter-docs.html),
[MCP transports](https://modelcontextprotocol.io/specification/2026-07-28/basic/transports).
Both modules pin Spring AI BOM 2.0.1; it manages MCP Java SDK 2.0.0. The server
uses spring-ai-starter-mcp-server-webmvc, STREAMABLE; backend standard client
starter uses the synchronous JDK HTTP transport. No WebFlux application or
deprecated standalone SSE setup. SDK negotiates a mutually supported protocol;
the latest specification revision is not a promise every negotiated feature is
enabled. SDK transport handles framing/negotiation, not handwritten JSON-RPC.

Server module: mcp/documentation-server, independent Maven build, Java 25/Boot
4.1.1. Reuse backend wrapper with `-f ../mcp/documentation-server/pom.xml` from
backend, then run the packaged server JAR. Port defaults to 8081 on localhost.
The fixed packaged corpus contains educational recommendation/payments runbooks,
not real operational secrets. It exposes only searchDocumentation with bounded
typed arguments/results. Resources/prompts/completion are disabled: adding them
would teach unrelated features without serving this investigation.

Backend profile `mcp` replaces only documentation's stub. Connection is lazy,
discovery verifies the fixed tool name, and only that capability is invoked;
remote discovery never authorizes a new tool. Failure becomes bounded evidence
failure. Documentation is optional, so later Java workflows can report degraded
evidence. Request/initialization deadlines are one second each, overall tool
deadline two seconds. No application startup dependency on a reachable server.

The local server is read-only with non-sensitive corpus; bind to loopback or the
private Compose network with no published MCP port. Do not deploy it publicly:
remote/private runbooks need a separately reviewed authenticated MCP boundary.
Current application owner authorization remains before MCP dispatch, never in
the model. No model-supplied URL/file path is accepted.

Tests use a real random-port server and real SDK client for initialization,
discovery and invocation. Backend tests verify unavailable-server failure;
capstone verification will exercise runbooks flowing into a full investigation.
