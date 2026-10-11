#!/usr/bin/env bash
# compose로 띄운 앱에 초안 -> 저장 -> 승인 -> 시산표 흐름을 실제로 보낸다.
# 사용법: BASE=http://localhost:8080 bash scripts/smoke.sh
set -euo pipefail
BASE="${BASE:-http://localhost:8080}"
DATE="${VOUCHER_DATE:-$(date +%Y-%m-%d)}"
MONTH="${DATE:0:7}"

echo "## 1. readiness"
for i in $(seq 1 60); do
  if curl -fsS "$BASE/actuator/health/readiness" > /dev/null 2>&1; then break; fi
  sleep 2
done
curl -sS "$BASE/actuator/health/readiness"; echo

echo "## 2. draft (저장하지 않음)"
DRAFT=$(curl -sS -X POST "$BASE/api/vouchers/draft" -H 'Content-Type: application/json' -d "{
  \"description\": \"사무용품 카드 구입\", \"voucherDate\": \"$DATE\", \"counterpartyName\": \"가상문구\",
  \"evidenceType\": \"CARD\", \"vatType\": \"TAXABLE\", \"supplyAmount\": 100000 }")
echo "$DRAFT"; echo

echo "## 3. save (초안 라인을 그대로 저장)"
LINES=$(echo "$DRAFT" | python3 -c 'import json,sys; d=json.load(sys.stdin); print(json.dumps([{"accountCode":l["accountCode"],"side":l["side"],"amount":l["amount"]} for l in d["draft"]["lines"]]))')
SAVED=$(curl -sS -X POST "$BASE/api/vouchers" -H 'Content-Type: application/json' -d "{
  \"description\": \"사무용품 카드 구입\", \"voucherDate\": \"$DATE\", \"counterpartyName\": \"가상문구\",
  \"evidenceType\": \"CARD\", \"vatType\": \"TAXABLE\", \"supplyAmount\": 100000, \"drafter\": \"fixture\",
  \"preparer\": \"김작성\", \"lines\": $LINES }")
echo "$SAVED"; echo
ID=$(echo "$SAVED" | python3 -c 'import json,sys; print(json.load(sys.stdin)["id"])')

echo "## 4. approve"
curl -sS -X POST "$BASE/api/vouchers/$ID/approve" -H 'Content-Type: application/json' -d '{"approver":"검토자"}'; echo; echo

echo "## 5. FAIL 전표 저장 후 승인 시도 (409 기대)"
FAILED=$(curl -sS -X POST "$BASE/api/vouchers" -H 'Content-Type: application/json' -d "{
  \"description\": \"차대 불일치\", \"voucherDate\": \"$DATE\", \"counterpartyName\": \"가상문구2\",
  \"evidenceType\": \"CARD\", \"vatType\": \"TAXABLE\", \"supplyAmount\": 100000, \"preparer\": \"김작성\",
  \"lines\": [{\"accountCode\":\"830\",\"side\":\"DEBIT\",\"amount\":100000},{\"accountCode\":\"135\",\"side\":\"DEBIT\",\"amount\":10000},{\"accountCode\":\"101\",\"side\":\"CREDIT\",\"amount\":100000}] }")
echo "$FAILED"; echo
FID=$(echo "$FAILED" | python3 -c 'import json,sys; print(json.load(sys.stdin)["id"])')
curl -sS -o /dev/stderr -w "HTTP %{http_code}\n" -X POST "$BASE/api/vouchers/$FID/approve" -H 'Content-Type: application/json' -d '{"approver":"검토자"}'; echo

echo "## 6. trial balance"
curl -sS "$BASE/api/reports/trial-balance?month=$MONTH"; echo
