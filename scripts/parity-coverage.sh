#!/usr/bin/env bash
# TASK-14: the parity-coverage suite body. Wraps fetch + reconcile so verify.sh can run it
# through run_suite like every other suite (verdict = printed RESULT status AND exit code).
# TASK-14.2: --execution adds the post-build identity reconciliation of the per-class
# surefire reports (every declared mapped case must run or carry a lawful skip
# disposition); verify.sh runs this suite only after a green build, so the reports exist.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"

"$ROOT/scripts/fetch-upstream.sh"
exec python3 "$ROOT/scripts/check-parity-coverage.py" --execution
