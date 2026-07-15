#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MODE="${1:-default}"

run_gradle() {
  (cd "$ROOT_DIR" && ./gradlew "$@")
}

run_web_build() {
  if [[ -f "$ROOT_DIR/web/admin/package.json" ]]; then
    (cd "$ROOT_DIR/web/admin" && npm run build)
  fi
}

case "$MODE" in
  default|quick)
    run_gradle test
    ;;
  full)
    run_gradle test
    run_web_build
    ;;
  web)
    run_web_build
    ;;
  domain)
    run_gradle :core:domain:test
    ;;
  config)
    run_gradle :core:config:test
    ;;
  sync)
    run_gradle :core:sync:test :server:test
    ;;
  hardware)
    run_gradle :core:hardware:test
    ;;
  android-db)
    run_gradle :core:database:testDebugUnitTest :core:database:testReleaseUnitTest :core:database:connectedDebugAndroidTest
    ;;
  *)
    cat >&2 <<USAGE
Usage: ./scripts/regression.sh [mode]

Modes:
  default | quick Run Gradle tests only
  full            Run Gradle tests and web admin build
  web             Run web admin build only
  domain          Run core domain tests
  config          Run core config tests
  sync            Run core sync and server tests
  hardware        Run hardware abstraction tests
  android-db      Run Room unit tests and connected DAO test
USAGE
    exit 2
    ;;
esac
