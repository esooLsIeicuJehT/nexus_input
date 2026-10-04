#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
OUT="${1:-$ROOT/NEXUS_INPUT-KernelSU-Companion.zip}"
rm -f "$OUT"
(
  cd kernelsu-module
  zip -r "$OUT" . -x 'README.md'
)
echo "$OUT"
