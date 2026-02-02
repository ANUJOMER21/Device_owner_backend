#!/bin/bash
# Test Firebase status: login to get admin JWT, then call /api/admin/firebase/status

set -e

BASE_URL="${BASE_URL:-https://emi-locker-api.duckdns.org:8080}"
ADMIN_PASSWORD="${ADMIN_PASSWORD:-admin123}"

echo "=== Firebase status check ==="
echo "Base URL: $BASE_URL"
echo ""

# 1. Login and get token
echo "1. Logging in (POST $BASE_URL/api/admin/login)..."
RESPONSE=$(curl -k -s -X POST "$BASE_URL/api/admin/login" \
  -H "Content-Type: application/json" \
  -d "{\"password\":\"$ADMIN_PASSWORD\"}")

if echo "$RESPONSE" | grep -q '"success":true'; then
  # Extract token (works with or without jq)
  if command -v jq >/dev/null 2>&1; then
    TOKEN=$(echo "$RESPONSE" | jq -r '.token // .data.token // empty')
  else
    TOKEN=$(echo "$RESPONSE" | sed -n 's/.*"token"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p' | head -1)
  fi

  if [ -z "$TOKEN" ] || [ "$TOKEN" = "null" ]; then
    echo "   ❌ Login succeeded but could not extract token from response."
    echo "   Response: $RESPONSE"
    exit 1
  fi
  echo "   ✅ Login OK, token obtained."
else
  echo "   ❌ Login failed."
  echo "   Response: $RESPONSE"
  exit 1
fi

echo ""

# 2. Call Firebase status
echo "2. Calling Firebase status (GET $BASE_URL/api/admin/firebase/status)..."
STATUS_RESPONSE=$(curl -k -s -H "Authorization: Bearer $TOKEN" "$BASE_URL/api/admin/firebase/status")

if [ -z "$STATUS_RESPONSE" ]; then
  echo "   ❌ No response from Firebase status API."
  exit 1
fi

echo "   Response:"
if command -v jq >/dev/null 2>&1; then
  echo "$STATUS_RESPONSE" | jq .
else
  echo "$STATUS_RESPONSE"
fi

# Summary
if echo "$STATUS_RESPONSE" | grep -q '"initialized":true'; then
  echo ""
  echo "✅ Firebase is initialized."
  exit 0
else
  echo ""
  echo "⚠️  Firebase is NOT initialized (initialized: false or missing)."
  exit 1
fi
