# Cashier → Web → AI Demo Runbook

## Fixed environment

- JDK 17 and Node.js 20
- Android API 35 emulator
- Ktor on `http://localhost:8080` with H2 in-memory storage
- Web Admin on `http://localhost:5173`
- Cashier server URL: `http://10.0.2.2:8080`
- Cashier PIN: `2222`
- Web Admin: `admin@pos.local` / `admin123`

Set the server-only environment variables before starting Ktor:

```bash
export DEEPSEEK_API_KEY="<provide-at-demo-time>"
export DEEPSEEK_BASE_URL="https://api.deepseek.com"
export DEEPSEEK_MODEL="deepseek-v4-flash"
export DEMO_MODE="true"
export JWT_SECRET="demo-local-secret-change-me"
```

Never put the API key in a frontend environment file, terminal recording, log, or Git commit.

## Start

Terminal 1:

```bash
./gradlew :server:run
```

Terminal 2:

```bash
cd web/admin
npm ci
npm run dev
```

Build the Cashier APK:

```bash
./gradlew :app:cashier:assembleDebug
```

## Demo flow

1. Open Cashier and sign in with PIN `2222`.
2. Open a table, add items, add one more item, and close with cash.
3. Open Web Admin and confirm that the order appears within 15 seconds.
4. Confirm Dashboard order count, net revenue, and average order value.
5. Click **Generate AI operating insight**.
6. Confirm the response uses the current snapshot and contains observations plus three actions.

## Release gate

- GitHub Actions is green.
- Cashier builds and installs on the API 35 emulator.
- Five consecutive cash orders sync correctly.
- Dashboard totals match the order list.
- AI failures do not affect orders, sync, or reports.
- Three clean-data rehearsals pass.
- `git status` is clean and no secret, database, APK, build output, or dependency directory is tracked.
