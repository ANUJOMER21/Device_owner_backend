#!/usr/bin/env bash
# Start backend with AWS RDS PostgreSQL.
# Loads DB_HOST and DB_PASSWORD from keys.txt if present (gitignored).
# Or set env vars: DB_HOST, DB_PASSWORD, DB_NAME, DB_USERNAME, DB_SSLMODE

set -e

cd "$(dirname "$0")"

# Load from keys.txt if present
if [ -f keys.txt ]; then
    RDS_HOST=$(grep "RDSHOST=" keys.txt 2>/dev/null | sed 's/.*RDSHOST="\([^"]*\)".*/\1/' | head -1)
    PG_PASS=$(grep "postgres_password:" keys.txt 2>/dev/null | cut -d: -f2 | tr -d ' \r' | head -1)
    [ -n "$RDS_HOST" ] && export DB_HOST="${DB_HOST:-$RDS_HOST}"
    [ -n "$PG_PASS" ] && export DB_PASSWORD="${DB_PASSWORD:-$PG_PASS}"
fi

# AWS RDS requires SSL
# RDS default db is "postgres" - use postgres unless da_emilocker exists (run ./create-rds-da-emilocker-db.sh first)
export DB_SSLMODE="${DB_SSLMODE:-require}"
# Prefer postgres if DB_NAME not explicitly set (avoids "database does not exist" when da_emilocker not created)
export DB_NAME="${DB_NAME:-postgres}"
export DB_USERNAME="${DB_USERNAME:-postgres}"

if [ -z "$DB_HOST" ] || [ -z "$DB_PASSWORD" ]; then
    echo "⚠️  AWS RDS: Set DB_HOST and DB_PASSWORD (or add to keys.txt)"
    echo "   Example: export DB_HOST=\"da-emilocker-db.xxx.eu-north-1.rds.amazonaws.com\""
    echo "            export DB_PASSWORD=\"your-password\""
    echo ""
fi

echo "🚀 Starting backend with AWS RDS PostgreSQL..."
echo "   If you get 'Connect timed out': run ./run-local-db.sh for local DB, or fix RDS Security Group"
echo "   Host: ${DB_HOST:-<not set>}"
echo "   DB: $DB_NAME | Health: http://localhost:8080/api/health/db"
echo ""

./gradlew bootRun
