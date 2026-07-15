# AI Menu Price Agent API

This is the first controlled write-plane API. DeepSeek may structure a natural-language instruction, but the server resolves menu items, calculates all money fields, persists the proposal, validates permissions and versions again at execution time, performs the mutation, and writes the audit record.

Both endpoints require a valid admin JWT and the `menu.edit` permission. The feature must also be enabled server-side with `AI_AGENT_ENABLED=true` and `AI_AGENT_PRICE_UPDATE_ENABLED=true`.

Money is always represented as integer minor units. The client must never calculate or resend an executable price.

## Create proposal

`POST /admin/ai/price-proposals`

Request:

```json
{
  "instruction": "把宫保鸡丁价格提高 5 元",
  "locale": "zh-CN"
}
```

Successful response (`200`):

```json
{
  "proposalId": "01900000-0000-7000-8000-000000000001",
  "status": "PROPOSED",
  "tool": "menu.update_price",
  "createdAt": 1784102400000,
  "expiresAt": 1784102700000,
  "requiresConfirmation": true,
  "currencyCode": "CNY",
  "minorUnitDigits": 2,
  "changes": [
    {
      "itemId": "item-1",
      "itemName": "宫保鸡丁",
      "oldPriceMinorUnit": 3800,
      "newPriceMinorUnit": 4300,
      "deltaMinorUnit": 500,
      "deltaPercentBasisPoints": 1316
    }
  ],
  "warnings": []
}
```

`deltaPercentBasisPoints` is calculated by the server and is `null` when the old price is zero. `expiresAt` is an epoch-millisecond timestamp. A proposal is immutable after creation and always requires explicit confirmation.

## Execute proposal

`POST /admin/ai/price-proposals/{proposalId}/execute`

Request:

```json
{
  "confirmed": true,
  "idempotencyKey": "01900000-0000-7000-8000-000000000002"
}
```

The body deliberately contains no item IDs or price fields. The server executes the canonical values stored with `proposalId` only when the menu item's current `updatedAt` still equals the version captured by the proposal.

Successful response (`200`):

```json
{
  "proposalId": "01900000-0000-7000-8000-000000000001",
  "status": "EXECUTED",
  "executedAt": 1784102460000,
  "auditId": "01900000-0000-7000-8000-000000000003",
  "idempotentReplay": false
}
```

Replaying the same proposal with the same idempotency key returns the original successful result with `idempotentReplay: true`. Reusing that key for another proposal fails with `AI_IDEMPOTENCY_CONFLICT`. Executing an already executed proposal with a different key fails with `AI_PROPOSAL_ALREADY_EXECUTED`.

## Stable errors

All controlled write-plane errors use:

```json
{"code":"AI_PROPOSAL_STALE","message":"Menu item changed after this proposal was created","retryable":false}
```

| HTTP | Code | Meaning |
|---:|---|---|
| 400 | `AI_INVALID_REQUEST` | Missing/invalid instruction, confirmation, locale, or idempotency key |
| 401 | `AI_UNAUTHORIZED` | Missing or invalid JWT |
| 403 | `AI_PERMISSION_DENIED` | Caller lacks `menu.edit` |
| 404 | `AI_PROPOSAL_NOT_FOUND` | Proposal ID does not exist |
| 409 | `AI_PROPOSAL_STALE` | Menu item version changed after proposal creation |
| 409 | `AI_PROPOSAL_ALREADY_EXECUTED` | Proposal was executed using another idempotency key |
| 409 | `AI_IDEMPOTENCY_CONFLICT` | Idempotency key belongs to another proposal |
| 410 | `AI_PROPOSAL_EXPIRED` | Confirmation window elapsed |
| 422 | `AI_TARGET_AMBIGUOUS` | Instruction matches zero or multiple menu items |
| 503 | `AI_AGENT_DISABLED` | Agent or price-update capability flag is disabled |

Provider failures retain the existing read-plane codes: `AI_NOT_CONFIGURED`, `AI_AUTH_FAILED`, `AI_QUOTA_EXCEEDED`, `AI_RATE_LIMITED`, `AI_PROVIDER_UNAVAILABLE`, `AI_INVALID_RESPONSE`, and `AI_TIMEOUT`. Provider failure never mutates menu data.

## Security and audit invariants

- The DeepSeek key stays in server environment/Keychain and is never returned to the client or written to logs/audit payloads.
- DeepSeek receives only the instruction, locale, currency metadata, and the minimum menu candidate fields required for intent resolution.
- Proposal and execute both enforce `menu.edit`; execute rechecks it even if proposal creation succeeded.
- Execution never rebases a proposal onto a newer menu price. A stale proposal must be discarded and recreated.
- Every successful mutation records actor ID, proposal ID, tool name, item ID, before/after values, timestamp, and idempotency key under the returned `auditId`.
