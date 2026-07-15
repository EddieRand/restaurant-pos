# AI Operating Insight API

`POST /admin/ai/operating-insight` requires a valid admin JWT and the `report.daily` permission.

Request:

```json
{"fromMs": 0, "toMs": 0, "locale": "zh-CN"}
```

The response contains the requested period, the server-computed snapshot, a headline and summary, observations, and exactly three actions. The server sends only aggregate restaurant metrics to DeepSeek. Customer names, phone numbers, notes, and individual payment records are excluded.

Stable error codes:

- `AI_INVALID_REQUEST` — invalid period or locale
- `AI_NOT_CONFIGURED` — missing server-side API key
- `AI_AUTH_FAILED` — provider rejected the API key
- `AI_QUOTA_EXCEEDED` — provider balance or quota is insufficient
- `AI_RATE_LIMITED` — provider rate limit
- `AI_PROVIDER_UNAVAILABLE` — transient provider failure
- `AI_INVALID_RESPONSE` — empty or invalid provider JSON
- `AI_TIMEOUT` — the 20-second server deadline elapsed
