#!/usr/bin/env bash
# TASK-14: retrieve the pinned upstream clone and verify its provenance.
#
# Usage: scripts/fetch-upstream.sh [clone-path]
# The clone path defaults to $ANTIWIRE_UPSTREAM or /tmp/wire. The script is idempotent:
# an existing clone is reused after the pin check passes.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
PINS="$ROOT/config/parity-pins.json"

PINS_LINE="$(python3 - "$PINS" <<'PY'
import json, sys
pins = json.load(open(sys.argv[1]))
u = pins["upstream"]
print(u["repository"], u["tag"], u["annotated_tag_object"], u["resolved_commit"],
      pins["clone_path_default"])
PY
)"
read -r REPO TAG TAG_OBJECT COMMIT CLONE_DEFAULT <<<"$PINS_LINE"

CLONE_PATH="${1:-${ANTIWIRE_UPSTREAM:-$CLONE_DEFAULT}}"

verify_pin() {
  local tag_object commit
  tag_object="$(git -C "$CLONE_PATH" rev-parse "$TAG^{tag}")"
  commit="$(git -C "$CLONE_PATH" rev-parse "$TAG^{commit}")"
  if [ "$tag_object" != "$TAG_OBJECT" ] || [ "$commit" != "$COMMIT" ]; then
    echo "FAIL: $CLONE_PATH does not match the pinned upstream identity." >&2
    echo "  tag $TAG resolves to object $tag_object commit $commit" >&2
    echo "  pins expect object $TAG_OBJECT commit $COMMIT" >&2
    echo "  re-run with a fresh clone path or update config/parity-pins.json per docs/parity-runner.md" >&2
    return 1
  fi
  echo "upstream pin verified: $TAG -> $COMMIT (annotated object $TAG_OBJECT) at $CLONE_PATH"
}

if [ ! -d "$CLONE_PATH/.git" ]; then
  mkdir -p "$(dirname "$CLONE_PATH")"
  git clone --branch "$TAG" "$REPO" "$CLONE_PATH"
fi

# An existing clone (cache refill, default-branch checkout) may not carry the pinned tag.
if ! git -C "$CLONE_PATH" rev-parse -q --verify "$TAG^{tag}" >/dev/null; then
  git -C "$CLONE_PATH" fetch origin "refs/tags/$TAG:refs/tags/$TAG" --force
fi
verify_pin
# The pin constrains refs; reconciliation reads the working tree, so put the tree AT the pin.
git -C "$CLONE_PATH" checkout --quiet --detach "$COMMIT"

# A locally modified clone is not the pinned sources: reconciliation reads files, not
# refs, so a dirty worktree would silently reconcile against edited upstream tests.
if [ -n "$(git -C "$CLONE_PATH" status --porcelain)" ]; then
  echo "FAIL: $CLONE_PATH has local modifications; reconciliation reads the working tree," >&2
  echo "  so a modified clone is rejected. Restore it with:" >&2
  echo "    git -C $CLONE_PATH checkout --quiet --detach $COMMIT" >&2
  echo "    git -C $CLONE_PATH clean -fd" >&2
  exit 1
fi
