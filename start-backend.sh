#!/bin/bash

# Backend Startup Script
# This script checks prerequisites and starts the backend server

set -e

echo "🚀 Starting DA EMI Locker Backend..."
echo ""

# Check Java
echo "📋 Checking Java installation..."
if ! command -v java &> /dev/null; then
    echo "❌ Java is not installed. Please install Java 17."
    exit 1
fi

JAVA_VERSION=$(java -version 2>&1 | awk -F '"' '/version/ {print $2}' | cut -d'.' -f1)
if [ "$JAVA_VERSION" -lt 17 ]; then
    echo "❌ Java 17 or higher is required. Found Java $JAVA_VERSION"
    exit 1
fi
echo "✅ Java $(java -version 2>&1 | head -n 1)"

# Check PostgreSQL
echo ""
echo "📋 Checking PostgreSQL connection..."
if command -v pg_isready &> /dev/null; then
    if pg_isready -h localhost -p 5432 > /dev/null 2>&1; then
        echo "✅ PostgreSQL is running"
    else
        echo "⚠️  PostgreSQL might not be running. Attempting to start anyway..."
    fi
else
    echo "⚠️  pg_isready not found. Skipping PostgreSQL check."
fi

# Check database exists
echo ""
echo "📋 Checking database..."
DB_NAME=${DB_NAME:-da_emilocker}
if command -v psql &> /dev/null; then
    if psql -U postgres -lqt | cut -d \| -f 1 | grep -qw "$DB_NAME"; then
        echo "✅ Database '$DB_NAME' exists"
    else
        echo "⚠️  Database '$DB_NAME' might not exist. Creating..."
        createdb -U postgres "$DB_NAME" 2>/dev/null || echo "   (You may need to create it manually)"
    fi
else
    echo "⚠️  psql not found. Skipping database check."
fi

# Start server
echo ""
echo "🚀 Starting backend server..."
echo "   Server will be available at: http://localhost:8080"
echo "   Health check: http://localhost:8080/api/health"
echo "   Admin health: http://localhost:8080/api/admin/health"
echo ""
echo "Press Ctrl+C to stop the server"
echo ""

cd "$(dirname "$0")"
./gradlew bootRun
