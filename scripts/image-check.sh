#!/usr/bin/env bash
# Starts the image, records how long it takes to answer /_twinrail/health, runs a smoke test and prints the
# image size. Usage: scripts/image-check.sh <image>   (default: twinrail)
# Writes a Markdown summary to $GITHUB_STEP_SUMMARY when that is set.
set -euo pipefail

image="${1:-twinrail}"
port=18080
name="twinrail-check-$$"
trap 'docker rm -f "$name" >/dev/null 2>&1 || true' EXIT

now_ms() { date +%s%3N; }

start=$(now_ms)
docker run -d --name "$name" -p "$port:8080" "$image" >/dev/null
until curl -sf "http://localhost:$port/_twinrail/health" >/dev/null; do
  if [ "$(docker inspect -f '{{.State.Running}}' "$name")" != "true" ]; then
    echo "The container stopped before it became healthy. Logs:" >&2
    docker logs "$name" >&2
    exit 1
  fi
  if [ $(( $(now_ms) - start )) -gt 60000 ]; then
    echo "No healthy answer within 60 s. Logs:" >&2
    docker logs "$name" >&2
    exit 1
  fi
  sleep 0.05
done
startup_ms=$(( $(now_ms) - start ))

# Smoke test: a payment is accepted and settles, and the response names its state.
response=$(curl -s -X POST "http://localhost:$port/messages" -H 'Content-Type: application/xml' --data-binary @- <<'XML'
<Document xmlns="urn:iso:std:iso:20022:tech:xsd:pacs.008.001.08"><FIToFICstmrCdtTrf>
<GrpHdr><MsgId>SMOKE-1</MsgId><NbOfTxs>1</NbOfTxs></GrpHdr>
<CdtTrfTxInf><PmtId><EndToEndId>E2E-1</EndToEndId><UETR>bdd73226-2feb-4e95-a8ef-e333b266f103</UETR></PmtId>
<IntrBkSttlmAmt Ccy="CAD">10.00</IntrBkSttlmAmt>
<DbtrAgt><FinInstnId><ClrSysMmbId><MmbId>000000001</MmbId></ClrSysMmbId></FinInstnId></DbtrAgt>
<CdtrAgt><FinInstnId><ClrSysMmbId><MmbId>000000002</MmbId></ClrSysMmbId></FinInstnId></CdtrAgt>
</CdtTrfTxInf></FIToFICstmrCdtTrf></Document>
XML
)
echo "$response" | grep -q '"state":"SETTLED"' || { echo "Smoke test failed. Response: $response" >&2; exit 1; }

size_mb=$(( $(docker image inspect -f '{{.Size}}' "$image") / 1024 / 1024 ))
echo "Image: $image, size ${size_mb} MB, time to healthy ${startup_ms} ms, smoke test passed."
if [ -n "${GITHUB_STEP_SUMMARY:-}" ]; then
  {
    echo "### Container image"
    echo ""
    echo "| Measure | Value |"
    echo "| --- | --- |"
    echo "| Image size | ${size_mb} MB |"
    echo "| Time from \`docker run\` to healthy | ${startup_ms} ms |"
  } >> "$GITHUB_STEP_SUMMARY"
fi
