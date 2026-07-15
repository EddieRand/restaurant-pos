# AI Workspace API

The AI Workspace is one conversational surface over two isolated planes: read-only analysis and controlled write proposals. The planner may select only registered tools; it never receives database, SQL, arbitrary HTTP, or direct mutation access.

All endpoints require a valid admin JWT. The first release accepts `locale: "zh-CN"` only.

## Experts and tools

| Expert | Allowed tools |
|---|---|
| `AUTO` | All tools the current operator may use |
| `OPERATIONS` | `report.operating_insight`, `report.query` |
| `PRODUCT_HELP` | `product.howto_search` |
| `MENU` | `report.query`, `menu.update_price` |

Registered tools are fixed to `report.operating_insight`, `report.query`, `product.howto_search`, and `menu.update_price`. The planner returns at most five ordered steps. Unknown tools or parameters fail closed.

`report.operating_insight` and `report.query` require `report.daily`. `menu.update_price` requires `menu.edit` during both proposal and execution. Product help requires authentication. Tool permissions are checked again immediately before every invocation.

## Sessions

### Create

`POST /admin/ai/workspace/sessions`

```json
{"expert":"AUTO","locale":"zh-CN"}
```

Returns an `AiWorkspaceSessionDto` with empty `messages` and `runs`.

### List and restore

```text
GET /admin/ai/workspace/sessions
GET /admin/ai/workspace/sessions/{sessionId}
```

Sessions are private to their creating admin user. The detail response contains persisted messages, runs, steps, results, proposal references, and errors. Every run includes its originating `messageId`, so restored clients can attach results to the correct conversation turn.

## Send a message

`POST /admin/ai/workspace/sessions/{sessionId}/messages`

```json
{
  "sessionId": "01900000-0000-7000-8000-000000000001",
  "expert": "AUTO",
  "message": "分析今天的营业情况，告诉我卖得最好的菜，并把宫保鸡丁涨价5元。",
  "locale": "zh-CN",
  "context": {"fromMs": 1784044800000,"toMs":1784131199999,"currentRoute":"/"}
}
```

The path and body session IDs must match. The server validates a non-empty message, a valid time range of at most 90 days, and a supported expert. It returns `202 Accepted`:

```json
{"sessionId":"...","messageId":"...","runId":"...","status":"QUEUED"}
```

Read steps run automatically. `menu.update_price` can only create an immutable proposal and stops in `AWAITING_CONFIRMATION`. It must never execute during message processing. Requests that ask the model to choose an unspecified price from analysis fail with `AI_CLARIFICATION_REQUIRED`.

## SSE progress and replay

`GET /admin/ai/workspace/runs/{runId}/events?afterSequence=0`

The Web client uses authenticated `fetch` streaming so the JWT remains in the Authorization header. The response content type is `text/event-stream`. Each event has an `id` equal to its persisted sequence and a JSON `data` payload matching `AiWorkspaceEventDto`.

Stable event types:

```text
message.accepted
plan.created
step.started
step.completed
step.awaiting_confirmation
step.failed
step.executed
run.completed
```

Events are persisted before they are emitted. Reconnecting with `afterSequence=N` replays all events with a larger sequence, then waits for new events until the run is terminal.

Planning can fail before a step exists. `AiWorkspaceRunDto.error` and `AiWorkspaceEventDto.error` therefore carry the same stable `{code,message,retryable}` shape used by step errors. A failed terminal event must include this error when the failure happened outside a tool step.

## Step results

Every step has one typed result in `AiWorkspaceStepResultDto`:

- `insight`: existing `AiOperatingInsightResponse`.
- `query`: a server-calculated answer, period, typed evidence, and source tool.
- `howTo`: answer, ordered steps, and cited help-document sections.
- `priceProposal`: existing `AiPriceProposalResponse`.
- `execution`: existing `ExecuteAiPriceProposalResponse`, populated after explicit confirmation.

Evidence numeric values use integer minor units, counts, or basis points. The model never calculates authoritative business values.

## Natural-language reporting limits

The report router may select only these server-side aggregates:

- Metrics: gross/net revenue, order count, guest count, average order value, discount, and refund.
- Dimensions: date, hour, weekday, menu item, and payment method.
- Maximum range: 90 days.
- Maximum ranked result count: 10.

Natural-language-to-SQL is prohibited. Customer identity, phone, notes, and individual payment records are excluded from model context.

## Product-help documents

Chinese help documents are versioned in the repository. Each document provides a stable ID, title, applicable route, keywords, last-verified timestamp, and heading-based sections. Answers must cite at least one retrieved source. No answer may invent UI controls or steps absent from the retrieved text.

## Controlled write execution

Workspace steps store only the `proposalId`. Confirmation continues to use the existing endpoint:

```text
POST /admin/ai/price-proposals/{proposalId}/execute
```

```json
{"confirmed":true,"idempotencyKey":"01900000-0000-7000-8000-000000000002"}
```

The client never resends menu item IDs or prices. Each proposal is confirmed separately. Existing expiry, stale-version, permission, idempotency, and audit behavior remains unchanged.

## Stable workspace errors

| HTTP | Code | Meaning |
|---:|---|---|
| 400 | `AI_INVALID_REQUEST` | Invalid session, message, locale, context, or event cursor |
| 401 | `AI_UNAUTHORIZED` | Missing or invalid JWT |
| 403 | `AI_PERMISSION_DENIED` | Tool permission denied |
| 404 | `AI_SESSION_NOT_FOUND` | Session missing or owned by another user |
| 404 | `AI_RUN_NOT_FOUND` | Run missing or owned by another user |
| 409 | `AI_RUN_IN_PROGRESS` | Another run is active in the session |
| 422 | `AI_CLARIFICATION_REQUIRED` | A safe plan needs explicit user parameters |
| 422 | `AI_UNSUPPORTED_INTENT` | No registered tool can satisfy the request |
| 503 | `AI_WORKSPACE_DISABLED` | Workspace feature flag is disabled |

Provider errors retain the existing `AI_NOT_CONFIGURED`, `AI_AUTH_FAILED`, `AI_QUOTA_EXCEEDED`, `AI_RATE_LIMITED`, `AI_PROVIDER_UNAVAILABLE`, `AI_INVALID_RESPONSE`, and `AI_TIMEOUT` codes. A failed AI run never affects ordinary menu, order, sync, or report APIs.
