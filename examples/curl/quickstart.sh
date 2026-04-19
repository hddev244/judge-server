#!/usr/bin/env bash
# quickstart.sh — end-to-end smoke test for judge-server
# Usage: JUDGE_URL=http://localhost:8080 API_KEY=sk_admin_local bash quickstart.sh

set -euo pipefail

JUDGE_URL="${JUDGE_URL:-http://localhost:8080}"
API_KEY="${API_KEY:-sk_admin_local}"
AUTH=(-H "X-API-Key: $API_KEY")

pass() { echo "OK"; }
fail() { echo "FAIL: $1"; exit 1; }

# ── 1/5  Health check ──────────────────────────────────────────────────────────
printf "[1/5] Health check ... "
health=$(curl -sf "$JUDGE_URL/actuator/health" | python3 -c "import sys,json; print(json.load(sys.stdin)['status'])" 2>/dev/null || true)
[[ "$health" == "UP" ]] || fail "expected UP, got '$health'"
pass

# ── 2/5  Create problem (idempotent) ──────────────────────────────────────────
printf "[2/5] Create problem 'quickstart-aplusb' ... "
existing=$(curl -sf "${AUTH[@]}" "$JUDGE_URL/api/v1/admin/problems?slug=quickstart-aplusb" 2>/dev/null \
           | python3 -c "import sys,json; d=json.load(sys.stdin); print(d['content'][0]['id'] if d.get('content') else '')" 2>/dev/null || true)

if [[ -n "$existing" ]]; then
  PROBLEM_ID="$existing"
  echo "already exists (id=$PROBLEM_ID)"
else
  create_resp=$(curl -sf -X POST "$JUDGE_URL/api/v1/admin/problems" \
    "${AUTH[@]}" -H "Content-Type: application/json" \
    -d '{"slug":"quickstart-aplusb","title":"A + B","description":"Print a+b.","timeLimitMs":2000,"memoryLimitKb":262144}')
  PROBLEM_ID=$(echo "$create_resp" | python3 -c "import sys,json; print(json.load(sys.stdin)['id'])")
  pass
fi

# ── 3/5  Upload test case ──────────────────────────────────────────────────────
printf "[3/5] Upload test case (3 5 → 8) ... "
tc_resp=$(curl -sf -X POST "$JUDGE_URL/api/v1/admin/problems/$PROBLEM_ID/testcases" \
  "${AUTH[@]}" -H "Content-Type: application/json" \
  -d '{"input":"3 5\n","expectedOutput":"8\n","isSample":true,"score":1}' 2>/dev/null || true)
[[ -n "$tc_resp" ]] || fail "no response from testcases endpoint"
pass

# ── 4/5  Publish problem ───────────────────────────────────────────────────────
printf "[4/5] Publish problem ... "
curl -sf -X PUT "$JUDGE_URL/api/v1/admin/problems/$PROBLEM_ID" \
  "${AUTH[@]}" -H "Content-Type: application/json" \
  -d '{"isPublished":true}' > /dev/null
pass

# ── 5/5  Submit C++ solution and wait for AC ──────────────────────────────────
printf "[5/5] Submit C++ solution ... "

SOURCE='#include<bits/stdc++.h>
using namespace std;
int main(){int a,b;cin>>a>>b;cout<<a+b<<endl;}'

submit_resp=$(curl -sf -X POST "$JUDGE_URL/api/v1/submissions" \
  "${AUTH[@]}" -H "Content-Type: application/json" \
  -d "{\"problemId\":$PROBLEM_ID,\"language\":\"cpp\",\"sourceCode\":$(python3 -c "import json,sys; print(json.dumps(sys.stdin.read()))" <<< "$SOURCE"),\"userRef\":\"quickstart\"}")

SUB_ID=$(echo "$submit_resp" | python3 -c "import sys,json; print(json.load(sys.stdin)['submissionId'])")
echo "submitted ($SUB_ID)"

printf "      Polling verdict"
for i in $(seq 1 15); do
  sleep 2
  printf "."
  verdict_resp=$(curl -sf "$JUDGE_URL/api/v1/submissions/$SUB_ID" "${AUTH[@]}")
  STATUS=$(echo "$verdict_resp" | python3 -c "import sys,json; print(json.load(sys.stdin)['status'])")
  if [[ "$STATUS" != "PENDING" && "$STATUS" != "JUDGING" ]]; then
    echo ""
    if [[ "$STATUS" == "AC" ]]; then
      echo "      Verdict: AC — PASS"
      exit 0
    else
      ERROR=$(echo "$verdict_resp" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('errorMessage',''))" 2>/dev/null || true)
      fail "verdict=$STATUS ${ERROR:+($ERROR)}"
    fi
  fi
done

echo ""
fail "timed out waiting for verdict on $SUB_ID"
