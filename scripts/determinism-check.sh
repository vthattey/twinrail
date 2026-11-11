#!/usr/bin/env bash
# "Same inputs, seed and clock produce the same messages and ids" must hold across JVM runs, not just
# within one. This starts the server in a fresh JVM three times, sends the same requests, and fails if
# any response differs by a single byte. Needs the distribution: ./gradlew :server:installDist
set -euo pipefail

# Relative paths from the repository root: a native Windows JVM cannot read MSYS-style absolute paths.
cd "$(dirname "$0")/.."
root="."
dist="server/build/install/twinrail"
port="${1:-18099}"
out="$(mktemp -d)"
pid=""
trap '[ -n "$pid" ] && kill "$pid" 2>/dev/null; rm -rf "$out"' EXIT

run() {
  TWINRAIL_PORT="$port" java -cp "$dist/lib/*" io.github.vthattey.twinrail.server.Main >/dev/null 2>&1 &
  pid=$!
  for _ in $(seq 1 150); do
    curl -sf "http://localhost:$port/_twinrail/health" >/dev/null && break
    sleep 0.2
  done
  local b="http://localhost:$port"
  {
    sed 's/125.50</0.00</' "$root/examples/messages/pacs008-credit-transfer.xml" | curl -s -X POST "$b/messages" --data-binary @-; echo
    curl -s -X POST "$b/_twinrail/webhooks" -d '{"url":"http://localhost:9/h","secret":"s"}'; echo
    curl -s "$b/_twinrail/webhooks"; echo
    curl -s -X PUT "$b/_twinrail/scenarios" --data-binary $'format: 1\nscenarios:\n  - id: m\n    action: { type: noc, code: C07, routing: "021000021", account: "12345", transactionCode: "22" }\n'; echo
    curl -s -X POST "$b/ach/files" --data-binary @"$root/examples/messages/ach-credits.ach"; echo
    curl -s -X POST "$b/_twinrail/clock/advance" -d '{"by":"PT1H"}'; echo
    curl -s "$b/_twinrail/payments"; echo
    curl -s "$b/_twinrail/webhooks/deliveries"; echo
    curl -s "$b/_twinrail/session"; echo
    curl -s "$b/_twinrail/accounts/123456789/statement"; echo
    curl -s "$b/ui/live"; echo
  } > "$1"
  kill "$pid" 2>/dev/null || true
  wait "$pid" 2>/dev/null || true
  pid=""
}

for n in 1 2 3; do
  run "$out/run$n.txt"
done

if cmp -s "$out/run1.txt" "$out/run2.txt" && cmp -s "$out/run1.txt" "$out/run3.txt"; then
  echo "Deterministic: three JVM launches gave identical responses ($(wc -c < "$out/run1.txt") bytes each)."
else
  echo "NOT deterministic: responses differ between JVM launches. First difference:" >&2
  diff "$out/run1.txt" "$out/run2.txt" | head -20 >&2 || true
  diff "$out/run1.txt" "$out/run3.txt" | head -20 >&2 || true
  exit 1
fi
