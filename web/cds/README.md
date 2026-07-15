# CDS — Customer Display System

Customer-facing transaction display for the restaurant POS. Shown to the customer during
ordering, tipping, payment, and receipt selection. **Not** a KDS, admin dashboard, or
order-status board.

Standalone React + Vite + Tailwind app, warm light-mode design, landscape 16:9. Best
viewed at 1920×1080 (the target display resolution).

## Run

```bash
cd web/cds
npm install
npm run dev      # http://localhost:5273/cds/
npm run build    # tsc (strict) + vite -> ../../server/src/main/resources/static/cds
```

Preview any screen with `?page=` (`welcome|order|tip|processing|success|receipt`), or the
floating dev switcher at the bottom (dev-only; not part of the customer UI).

## Structure

- `src/index.css` + `tailwind.config.js` — CDS design tokens (warm `#FAF8F5` bg,
  `#A9652B` accent, `#F4E8DC` soft, radius 20–28px). Aligned to the POS brand but its own
  token set; green (`#2E7D32`) is used **only** for the payment-success state.
- `src/types.ts`, `src/data/*` — data model + isolated mock data. UI is data-driven.
- `src/components/*` — reusable: `CDSPageShell`, `CDSHeader`, `StatusIllustration`,
  `OrderSummaryCard` / `OrderLineItem` / `TotalBreakdown`, `InfoBar`, `OrderBadge`,
  `TipOptionCard`, `PaymentStatusCard`, `ReceiptMethodSelector`, `Icon`.
- `src/pages/*` — the six screens.

## Wiring to real POS later

`App.tsx` currently drives `page` + mock data. To connect to live POS state, replace the
mock imports / `page` state with the real order + payment feed (e.g. poll/subscribe to the
order being checked out) and map it onto the existing `CdsOrder` / `CdsPayment` / tip-option
types. Components need no changes — they already render from props. Money in the POS is
stored in minor units (cents); convert to the whole-currency numbers this layer expects.
