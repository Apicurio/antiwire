#!/usr/bin/env bash
# TASK-14: the parity-coverage suite body. Wraps fetch + reconcile so verify.sh can run it
# through run_suite like every other suite (verdict = printed RESULT status AND exit code).
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"

"$ROOT/scripts/fetch-upstream.sh"
exec python3 "$ROOT/scripts/check-parity-coverage.py"
