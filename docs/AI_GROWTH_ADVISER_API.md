# AI Growth Adviser P0 contract

Growth Adviser remains inside the unified `/ai` workspace. `GROWTH` is an expert
restriction, not an elevated identity. It can route only to
`growth.daily_briefing`, `growth.content_draft`, and
`crm.coupon_campaign_proposal`.

## Data truth labels

- `REAL`: POS, CRM, coupon, campaign, and group-buying redemption aggregates.
- `AI_GENERATED`: DeepSeek prose grounded in supplied evidence.
- `DEMO_SIGNAL`: illustrative Douyin trend or advertising signals. Every such
  result must show `演示信号，不代表抖音官方数据`.

## HTTP

```text
GET  /admin/ai/growth/briefings/today
POST /admin/ai/growth/proposals/{proposalId}/revise
POST /admin/ai/growth/proposals/{proposalId}/execute
```

Execute accepts only `{ "confirmed": true, "idempotencyKey": "..." }`. It never
accepts coupon amount, audience, item IDs, or model parameters. Revising a
proposal creates a new proposal ID and invalidates the old version.

Successful execution creates a real fixed-amount coupon and a CRM campaign in
`DRAFT` status. It does not send SMS, platform messages, or publish content.

## Authorization and failures

Proposal and execute both require `crm.campaign.manage` and re-check it at the
server boundary. Stable business codes are `GROWTH_PERMISSION_DENIED`,
`GROWTH_PROPOSAL_NOT_FOUND`, `GROWTH_PROPOSAL_EXPIRED`,
`GROWTH_PROPOSAL_STALE`, `GROWTH_INVALID_PARAMS`,
`GROWTH_IDEMPOTENCY_CONFLICT`, and `GROWTH_ALREADY_EXECUTED`. Provider failures
retain the existing `AI_*` codes.
