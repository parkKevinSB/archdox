# AI Provider and Office Policy

ArchDox treats AI as an optional, metered platform capability. AI must never be
required for the deterministic document workflow to run.

## Core Rule

Provider API keys are platform secrets.

- Platform Admin stores provider credentials in Cloud API.
- Credentials are encrypted at rest with `ARCHDOX_AI_CREDENTIAL_MASTER_KEY`.
- Full API keys are never returned by API responses.
- ArchDox Agent does not receive provider API keys in MVP.
- Agent WebSocket receives only effective AI policy metadata.

## Permission Layers

Office AI policy is split into three switches.

- `aiEnabled`: whether the office may use AI at all
- `documentReviewAiEnabled`: whether pre-generation/post-generation AI review is allowed
- `documentGenerationAiEnabled`: whether AI text polishing/generation is allowed

The effective permission is:

```text
office.aiEnabled
AND requested feature switch
AND assigned provider credential is ACTIVE
```

If any condition fails, deterministic validation and normal document generation
must still work without AI.

## Runtime Execution

AI execution is gated in Cloud API before a Flower AI harness is created.

```text
REST AI review request
-> global feature flag check
-> office AI policy check
-> assigned provider credential check
-> DocumentReviewFlow submit
-> child flower-ai-harness flow uses providerCode:modelName
```

There are two review timings:

- `ReportPreflightHarness`: before document generation, reviews report input
  after deterministic validation passes.
- `DocumentQaHarness`: after document generation, reviews generated document
  artifacts.

Preflight review remains useful even when AI is disabled. In that case the flow
still runs deterministic validation and simply skips the child AI harness.

`providerCode` is the provider part of the harness `ModelId`.

Example:

```text
providerCode = openai-main
defaultModel = gpt-4.1-mini
ModelId      = openai-main:gpt-4.1-mini
```

Cloud API currently provides an ArchDox infrastructure implementation of the
`flower-ai-harness` `AiModelGateway`. This is not a new business-level AI
abstraction. It only bridges platform-managed provider credentials to the
harness runtime.

Spring AI is also attached behind this same `AiModelGateway` port. The harness
and ArchDox business flows do not depend on Spring AI directly. This keeps the
workflow stable while allowing provider adapters to be replaced by direct HTTP,
Spring AI `ChatClient`, local Ollama, or a test fake provider.

Supported executable adapters:

- `OPENAI`: OpenAI chat completions API
- `CUSTOM_HTTP`: OpenAI-compatible chat completions endpoint
- `OLLAMA`: Ollama `/api/chat`
- Spring AI adapter: delegates to `flower-ai-harness-spring-ai`
  `SpringAiModelGateway` for provider codes matching the configured prefix
- development fake provider: enabled only by configuration and provider code
  prefix, returns schema-valid harness JSON without external API calls

`GEMINI` and `ANTHROPIC` may be registered for planning, but direct execution
must stay disabled until provider-specific adapters are implemented. If a
gateway exposes them through an OpenAI-compatible API, register it as
`CUSTOM_HTTP`.

## Spring AI Adapter

ArchDox includes `flower-ai-harness-spring-boot-starter`, but ArchDox also owns
the primary `AiModelGateway` because provider credentials, call logs, cost
metadata, fake provider routing, and platform policy belong to ArchDox.

Therefore the Spring AI starter does not replace the ArchDox gateway. Instead,
ArchDox creates a `SpringAiModelGateway` when a Spring AI `ChatClient` /
`SpringAiModelResolver` is available, and the ArchDox gateway delegates to it
for selected provider codes.

Configuration:

```yaml
archdox:
  ai:
    spring-ai-adapter:
      enabled: false
      provider-code-prefix: spring-ai-
```

Runtime use:

```text
providerCode = spring-ai-openai
defaultModel = gpt-4.1-mini
ModelId      = spring-ai-openai:gpt-4.1-mini
```

Required Spring AI configuration still depends on the provider, for example:

```text
SPRING_AI_MODEL_CHAT=openai
OPENAI_API_KEY=...
OPENAI_MODEL=gpt-4.1-mini
ARCHDOX_AI_SPRING_AI_ADAPTER_ENABLED=true
```

Rules:

- Flow/Harness code must not import Spring AI.
- Spring AI is a provider adapter only.
- Spring AI adapter is off by default.
- When using office AI policy, the office still needs an active provider record
  such as `spring-ai-openai`; the API key itself may come from Spring AI
  environment configuration instead of the ArchDox encrypted credential table.

## Development Fake Provider

Local development and demos may run the real Flow/Harness path without a paid
API key by enabling the fake provider:

```yaml
archdox:
  ai:
    fake-provider:
      enabled: true
      provider-code-prefix: fake-
```

When enabled, any `ModelId` whose provider code starts with `fake-` returns a
schema-valid fake response for known harness prompts:

- `archdox-report-preflight`
- `archdox-document-qa`
- `archdox-ops-diagnosis`

Rules:

- fake provider is off by default
- fake provider belongs only in the provider adapter layer
- Flow and Harness code must not special-case fake AI
- fake provider is acceptable for local UI/manual testing and automated
  integration tests
- production environments must keep it disabled unless explicitly running a
  sandbox demo

## Development AI Bootstrap

`dev` and `local` profiles may also enable a small bootstrap that prepares fake
AI for manual UI testing.

```yaml
archdox:
  ai-review:
    enabled: true
  ai:
    fake-provider:
      enabled: true
    dev-bootstrap:
      enabled: true
      attach-to-existing-offices: true
```

When enabled, startup ensures:

- `fake-review` provider exists and is published
- `fake-ops` provider exists and is published
- existing offices are allowed to use AI review through `fake-review`
- document-generation AI remains off unless explicitly enabled
- credential delivery mode remains `PROXY_ONLY`

This is only a development convenience. It must not be used as a production
seeding mechanism. Production provider credentials and office policies must be
created through Platform Admin operations.

## Credential Delivery

MVP supports only:

```text
PROXY_ONLY
```

That means:

```text
Agent -> Cloud API AI proxy -> AI provider
```

The future enum values `EPHEMERAL_TOKEN` and `DIRECT_SECRET` are intentionally
reserved, but not enabled. Direct provider secret delivery to office Agents must
not be implemented casually because revocation, rotation, audit, and leak
response become much harder.

## Platform Admin APIs

```text
GET  /api/v1/platform-admin/ai/providers
POST /api/v1/platform-admin/ai/providers
PUT  /api/v1/platform-admin/ai/providers/{providerId}
POST /api/v1/platform-admin/ai/providers/{providerId}/publish
POST /api/v1/platform-admin/ai/providers/{providerId}/test

GET  /api/v1/platform-admin/ai/office-policies
PUT  /api/v1/platform-admin/ai/office-policies/{officeId}

GET  /api/v1/platform-admin/ai/call-logs
GET  /api/v1/platform-admin/ai/usage-summary

GET  /api/v1/platform-admin/ai/pricing-rules
POST /api/v1/platform-admin/ai/pricing-rules
POST /api/v1/platform-admin/ai/pricing-rules/{pricingRuleId}/disable
```

Publishing a provider or changing an office AI policy sends an
`AI_POLICY_CHANGED` WebSocket event to currently connected Agents for that
office. Reconnected Agents receive the current effective policy in `WELCOME`.

## Agent Message Shape

Agent policy messages contain no API key.

```json
{
  "type": "AI_POLICY_CHANGED",
  "agentId": 10,
  "aiPolicy": {
    "enabled": true,
    "documentReviewEnabled": true,
    "documentGenerationEnabled": false,
    "credentialDeliveryMode": "PROXY_ONLY",
    "policyVersion": 3,
    "apiKeyDelivered": false,
    "provider": {
      "credentialId": 1,
      "providerCode": "openai-main",
      "providerType": "OPENAI",
      "baseUrl": "https://api.openai.com/v1",
      "defaultModel": "gpt-4.1-mini",
      "credentialVersion": 2
    }
  }
}
```

## Cost Control

AI review/generation must be reached only after deterministic validation has
passed. Code-level validation handles required fields, checklist completion,
photo readiness, report status, and artifact availability first. AI is reserved
for judgment-heavy work such as wording, inconsistency detection, legal review
assistance, and suspicious omission detection.

Every provider call made through the ArchDox `AiModelGateway` is recorded in
`ai_model_call_logs`.

The log keeps provider/model, office, feature, workflow/resource reference,
success/failure, token counts when the provider returns them, latency, finish
reason, and a trimmed error message. This is the baseline for cost review and
later quota enforcement.

Model pricing is managed separately in `ai_model_pricing_rules`. A pricing rule
is keyed by:

```text
providerCode
modelName
currency
inputTokenPricePerMillion
outputTokenPricePerMillion
```

Use model name `*` as a provider-level fallback price. On every successful AI
call, Cloud API looks for an active exact provider/model rule first, then falls
back to provider/`*`. If token usage and a pricing rule are available, the
calculated input/output/total cost is snapshotted into `ai_model_call_logs`.

Pricing is intentionally a snapshot, not a live join. When a provider changes
its price, create a new pricing rule and disable the old one. Existing call logs
keep the historical estimate that was valid when the call was made.

Office AI policy also carries the first budget guard fields:

```text
budgetEnforcementEnabled
monthlyBudgetAmount
budgetCurrency
dailyCallLimit
monthlyTokenLimit
```

The execution gate checks these before creating an AI harness execution plan.
If the daily call limit, monthly token limit, or monthly cost budget has already
been reached, Cloud API rejects the AI execution with `AI_BUDGET_EXCEEDED`.

When monthly cost budget enforcement is enabled, the selected provider/model
must have an active pricing rule. The gateway accepts an exact model rule first
and then provider/model `*` as a fallback. This prevents pretending to enforce a
money budget when ArchDox cannot estimate the next call's cost.

AI harness flows must pass operational metadata through `ProviderOptions`, for
example:

```text
archdox.officeId
archdox.feature
archdox.workflowType
archdox.workflowKey
archdox.resourceType
archdox.resourceId
```

This keeps AI cost attribution outside prompt text and avoids hard-coding
workflow-specific logging logic inside provider adapters.

## AI Usage And Cost Operations

Platform Admin must be able to answer these questions without direct database
access:

- how many AI calls were made this month
- which offices used AI
- which features used AI, such as document review, document generation, legal
  review, template onboarding, or platform ops diagnosis
- which provider/model combinations were used
- how many calls succeeded or failed
- how many input/output tokens were consumed
- what estimated cost was recorded
- which offices are near or over budget
- whether a sudden cost spike came from one office, one feature, one model, or
  an operational diagnosis flow

The Platform Admin AI screen should treat these as first-class operations data,
not developer debugging data.

Recommended dashboard groups:

```text
this month summary
  call count
  success/failure count
  input/output tokens
  estimated total cost

office usage
  officeId / officeName
  feature
  call count
  token count
  estimated cost
  budget status

provider/model usage
  providerCode
  modelName
  call count
  token count
  estimated cost
  failure rate

recent call logs
  provider/model
  office
  feature
  workflow/resource reference
  status
  latency
  tokens
  estimated cost
  trimmed error
```

Budget guard and cost visibility are separate responsibilities:

- Budget guard decides whether a new AI execution is allowed.
- Usage/cost views explain what has already happened.
- Ops diagnosis uses usage/cost data to detect abnormal spikes, but it must not
  modify office policy or provider credentials without platform admin approval.

Usage data must stay platform-admin scoped. Normal office users should not see
cross-office AI usage. A later office admin billing/usage view may expose only
that office's own aggregate usage.

Cost values are estimates. They depend on provider token usage and active
pricing rules. If a provider does not return token usage or no pricing rule was
available, the call log must still be recorded, but cost may be empty.

## Operational Notes

- Provider credential changes are recorded in `operation_events`.
- Office AI policy changes are recorded per office in `operation_events`.
- AI provider calls are recorded in `ai_model_call_logs` and exposed to
  Platform Admin only.
- AI model pricing rules are managed by Platform Admin and used to estimate
  cost per call when provider token usage is available.
- AI usage summary is exposed to Platform Admin for the current month, grouped
  by office and feature.
- Platform Admin AI management must show usage and estimated cost as operational
  indicators, not only provider credential settings.
- Office AI budget guard can block execution before a harness is submitted.
- Preflight review API responses expose whether an AI child harness was planned
  and its current harness status, provider, model, attempt, and terminal reason.
- Platform Admin UI has an AI management section for provider registration,
  provider publishing, pricing rules, office policy updates, budget guard,
  usage summary, and recent AI call logs.
- Platform Admin may temporarily enable an in-memory AI observation mode while
  testing models. This mode captures the rendered prompt, raw model response,
  call metadata, token usage, latency, and error text for the most recent calls.
  It is not a permanent audit log and must not be used as the primary customer
  data store.
- AI observation mode defaults to off unless explicitly enabled by configuration
  or a platform-admin runtime action. Disabling it clears the in-memory buffer.
  Entries expire by TTL and are lost on server restart.
- Raw prompts and raw model responses should remain platform-admin only because
  they may contain customer project, site, report, photo, or checklist data.
- AWS/local/Tailscale deployments use the same application code; only provider
  credentials and policy settings differ.
