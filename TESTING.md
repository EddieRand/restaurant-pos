# Testing Regression Plan

This project treats tests as release gates for the POS architecture rules:
Room is the local source of truth, money uses `Long` minor units, business
logic stays in `core`/`feature`, and country-specific behavior stays out of
the core.

## Default Regression

Run the default local regression before handing off a change:

```bash
./scripts/regression.sh
```

This runs:

- `./gradlew test`

For a full release-style local regression, including the web admin production
bundle, run:

```bash
./scripts/regression.sh full
```

Use the narrower modes while iterating:

```bash
./scripts/regression.sh quick
./scripts/regression.sh web
./scripts/regression.sh android-db
```

## When To Add More Coverage

- Core business rules: run `./gradlew :core:domain:test`.
- Region, tax, currency, receipt, KDS, or kiosk config: run `./gradlew :core:config:test`.
- Offline sync or server API changes: run `./gradlew :core:sync:test :server:test`.
- Hardware abstraction changes: run `./gradlew :core:hardware:test`.
- Room schema, DAO, or migration changes: run `./gradlew :core:database:testDebugUnitTest :core:database:testReleaseUnitTest`; with an emulator or device, also run `./gradlew :core:database:connectedDebugAndroidTest`.
- Web admin changes: run `cd web/admin && npm run build`.

## Manual Smoke

Use a device or emulator for flow-level checks when UI, navigation, database,
sync, or KDS behavior changes:

1. PIN login succeeds.
2. Seat a table or create takeaway order.
3. Add a normal item, modifier item, and combo.
4. Place the order and confirm KDS tickets appear.
5. Complete cash checkout and confirm order/payment/table state.
6. Confirm shift report totals match order and payment data.

## Acceptance Criteria

- `./gradlew test` passes.
- Web admin changes pass `npm run build`.
- No new `Float` or `Double` money fields/calculations.
- No hardcoded country tax, compliance, or payment behavior in core/feature.
- App modules remain wiring/UI only; business rules stay in core/feature.
- SUNMI hardware claims must distinguish Mock test coverage from manual real-device validation.
