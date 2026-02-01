#!/bin/bash

# Quick Backend Health Check Script

echo "🔍 Checking backend server status..."
echo ""

# Check if server is responding
if curl -s -f http://localhost:8080/api/health > /dev/null 2>&1; then
    echo "✅ Backend server is running!"
    echo ""
    echo "Health Status:"
    curl -s http://localhost:8080/api/health | python3 -m json.tool 2>/dev/null || curl -s http://localhost:8080/api/health
    echo ""
    echo "Admin Health:"
    curl -s http://localhost:8080/api/admin/health | python3 -m json.tool 2>/dev/null || curl -s http://localhost:8080/api/admin/health
    echo ""
else
    echo "❌ Backend server is NOT running!"
    echo ""
    echo "To start the server, run:"
    echo "  cd /Users/anujomer/Desktop/backend"
    echo "  ./start-backend.sh"
    echo ""
    echo "Or manually:"
    echo "  ./gradlew bootRun"
    echo ""
    exit 1
fi
