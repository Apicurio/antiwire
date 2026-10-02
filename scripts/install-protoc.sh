#!/usr/bin/env bash
# TASK-15: install the pinned protoc binary (com.google.protobuf:protoc 4.36.1, the same
# artifact upstream's Gradle protobuf plugin resolves) into a cache dir with checksum
# verification. Prints the protoc executable path on stdout.
#
# Usage: scripts/install-protoc.sh [install-dir]
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PROTOC_VERSION=4.36.1
PROTOC_SHA256_OSX_AARCH64=dbd9a127dbbadd379bbea9a28a4349a0c9b1ad34b4c06f03fbe0f3853583a014
PROTOC_SHA256_OSX_X86_64=445b53a77c8ec0e2597c6fcb3ec3668fe378371903371150b1fb5a78f56fb0a4
PROTOC_SHA256_LINUX_X86_64=65143da2d7a01c7ab24c287fb06de20fb07ef0a346f17d39fc284393f0ca0457

case "$(uname -s)-$(uname -m)" in
  Darwin-arm64) CLASSIFIER=osx-aarch_64; SHA256=$PROTOC_SHA256_OSX_AARCH64 ;;
  Darwin-x86_64) CLASSIFIER=osx-x86_64; SHA256=$PROTOC_SHA256_OSX_X86_64 ;;
  Linux-x86_64) CLASSIFIER=linux-x86_64; SHA256=$PROTOC_SHA256_LINUX_X86_64 ;;
  *) echo "unsupported platform for pinned protoc: $(uname -s)-$(uname -m)" >&2; exit 1 ;;
esac

INSTALL_DIR="${1:-${PROTOC_HOME:-$HOME/.cache/antiwire-protoc}/$PROTOC_VERSION-$CLASSIFIER}"
mkdir -p "$INSTALL_DIR"
EXE="$INSTALL_DIR/protoc"

if [ ! -x "$EXE" ]; then
  TMP="$(mktemp)"
  trap 'rm -f "$TMP"' EXIT
  curl -sfL -o "$TMP" \
    "https://repo1.maven.org/maven2/com/google/protobuf/protoc/$PROTOC_VERSION/protoc-$PROTOC_VERSION-$CLASSIFIER.exe"
  ACTUAL="$(shasum -a 256 "$TMP" | cut -d' ' -f1)"
  if [ "$ACTUAL" != "$SHA256" ]; then
    echo "protoc checksum mismatch: expected $SHA256, got $ACTUAL" >&2
    exit 1
  fi
  mv "$TMP" "$EXE"
  chmod +x "$EXE"
  trap - EXIT
fi

echo "$EXE"
