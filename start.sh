#!/bin/bash

# AREA Backend Startup Script
echo "🚀 Starting AREA Backend..."

# Check if docker-compose is available
if ! command -v docker-compose &> /dev/null; then
    echo "❌ docker-compose is required but not installed."
    exit 1
fi

# Start PostgreSQL
echo "📦 Starting PostgreSQL database..."
docker-compose up -d postgres

# Wait for PostgreSQL to be ready
echo "⏳ Waiting for PostgreSQL to be ready..."
sleep 5

# Start the Spring Boot application
echo "🌟 Starting Spring Boot application..."
./gradlew bootRun

echo "✅ AREA Backend is now running!"
echo "📖 API Documentation: http://localhost:8080/swagger-ui.html"
echo "🔐 Authentication: admin / admin123"