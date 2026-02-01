#!/usr/bin/env bash
# Run backend with LOCAL PostgreSQL (ignores DB_HOST/DB_PASSWORD).
# Use this when AWS RDS is unreachable (Connect timed out) or for local dev.

set -e

cd "$(dirname "$0")"

# Unset AWS RDS env vars so app uses localhost
unset DB_HOST DB_PASSWORD DB_SSLMODE

echo "🚀 Starting backend with LOCAL PostgreSQL (localhost:5432/da_emilocker)..."
echo "   Use this when AWS RDS gives 'Connect timed out'"
echo "   Health: http://localhost:8080/api/health/db"
echo ""

./gradlew bootRun
