# Docker Setup Guide

This directory contains Docker Compose configurations for different use cases.

## Available Configurations

### 1. `docker-compose.db.yaml` - Database Services Only
**For: Backend Development Team**

This configuration only starts the database services (PostgreSQL, Redis) along with monitoring tools (Prometheus, Grafana). Use this when you're developing the backend locally and need the databases running.

```bash
# Start databases only
cd Docker
docker-compose -f docker-compose.db.yaml up -d

# Stop databases
docker-compose -f docker-compose.db.yaml down

# Stop and remove volumes (clean start)
docker-compose -f docker-compose.db.yaml down -v
```

**Services included:**
- PostgreSQL (port 5432)
- Redis (port 6379)
- Prometheus (port 9090)
- Grafana (port 3000)

### 2. `docker-compose.back.yaml` - Complete Backend Stack
**For: Frontend Development Team**

This configuration starts everything: the AREA backend application along with all its dependencies. Use this when you're developing the frontend and need a fully functional backend.

```bash
# Start complete backend stack
cd Docker
docker-compose -f docker-compose.back.yaml up -d

# View logs
docker-compose -f docker-compose.back.yaml logs -f

# Stop complete stack
docker-compose -f docker-compose.back.yaml down

# Stop and remove volumes (clean start)
docker-compose -f docker-compose.back.yaml down -v
```

**Services included:**
- AREA Backend Application (port 8080)
- PostgreSQL (port 5432)
- Redis (port 6379)
- Prometheus (port 9090)
- Grafana (port 3000)

## Prerequisites

1. **Docker and Docker Compose** installed
2. **Environment variables** configured (create a `.env` file in the project root)

### Required Environment Variables

Create a `.env` file in the project root (not in the Docker folder) with the following variables:

```env
# Database
DATABASE_NAME=area_db
DATABASE_USERNAME=area_user
DATABASE_PASSWORD=your_secure_password

# Application
SPRING_APPLICATION_NAME=AREA_Back
SPRING_PROFILES_ACTIVE=dev
SERVER_PORT=8080

# Security
ADMIN_USERNAME=admin
ADMIN_PASSWORD=your_admin_password
ADMIN_ROLES=ADMIN

# JWT Secrets (generate secure random strings)
JWT_ACCESS_SECRET=your_256bit_base64_encoded_access_secret_here
JWT_REFRESH_SECRET=your_256bit_base64_encoded_refresh_secret_here
ACCESS_TOKEN_EXPIRES_IN=15m
REFRESH_TOKEN_EXPIRES_IN=7d

# JPA
JPA_DDL_AUTO=none
JPA_SHOW_SQL=false
JPA_FORMAT_SQL=false

# Flyway
FLYWAY_ENABLED=true
FLYWAY_BASELINE_ON_MIGRATE=true

# Redis
REDIS_PASSWORD=
REDIS_HOST=localhost
REDIS_PORT=6379

# Swagger
SWAGGER_ENABLED=true
SWAGGER_UI_ENABLED=true

# Logging
LOG_LEVEL_APP=INFO
LOG_LEVEL_WEB=INFO
LOG_LEVEL_SECURITY=INFO

# Cache
CACHE_TTL=1800000
```

See `.env.example` in the project root for a complete template.

## Usage Examples

### For Backend Developers

When working on backend code:

```bash
# 1. Start only the databases
cd Docker
docker-compose -f docker-compose.db.yaml up -d

# 2. Run your backend application from your IDE or via Gradle
cd ..
./gradlew bootRun

# 3. When done, stop the databases
cd Docker
docker-compose -f docker-compose.db.yaml down
```

### For Frontend Developers

When working on frontend and need the complete backend:

```bash
# 1. Start the complete backend stack
cd Docker
docker-compose -f docker-compose.back.yaml up -d

# 2. Wait for the backend to be healthy (check logs)
docker-compose -f docker-compose.back.yaml logs -f area-backend

# 3. Access the backend API at http://localhost:8080
# 4. Access Swagger UI at http://localhost:8080/swagger-ui.html

# 5. When done, stop everything
docker-compose -f docker-compose.back.yaml down
```

## Accessing Services

Once the services are running:

- **Backend API**: http://localhost:8080
- **Swagger UI**: http://localhost:8080/swagger-ui.html
- **API Docs**: http://localhost:8080/v3/api-docs
- **Health Check**: http://localhost:8080/actuator/health
- **Metrics**: http://localhost:8080/actuator/metrics
- **Prometheus**: http://localhost:9090
- **Grafana**: http://localhost:3000 (default credentials: admin/admin)
- **PostgreSQL**: localhost:5432
- **Redis**: localhost:6379

## Troubleshooting

### Backend container fails to start

1. Check if the `.env` file exists and has all required variables
2. View container logs: `docker-compose -f docker-compose.back.yaml logs area-backend`
3. Check if ports are already in use
4. Ensure databases are healthy before backend starts

### Database connection issues

1. Check database container status: `docker-compose ps`
2. Verify environment variables in `.env`
3. Check database logs: `docker-compose -f docker-compose.db.yaml logs postgres`

### Port conflicts

If you get port conflicts:

1. Check what's using the port: `lsof -i :8080` (or the conflicting port)
2. Either stop the conflicting service or change the port in `.env`

### Clean restart

If you need to start fresh:

```bash
# Stop everything and remove volumes
docker-compose -f docker-compose.back.yaml down -v

# Rebuild the backend image
docker-compose -f docker-compose.back.yaml build --no-cache

# Start again
docker-compose -f docker-compose.back.yaml up -d
```

## Building the Backend Image

The backend image is built automatically when you run `docker-compose up`. To rebuild manually:

```bash
# From the Docker directory
docker-compose -f docker-compose.back.yaml build

# Or with no cache
docker-compose -f docker-compose.back.yaml build --no-cache
```

## Monitoring

Both configurations include Prometheus and Grafana for monitoring:

1. Access Grafana at http://localhost:3000
2. Default credentials: admin/admin (or as configured in `.env`)
3. Prometheus data source is pre-configured
4. Dashboards are available in `monitoring/grafana/dashboards/`

## Notes

- The backend application takes about 30-60 seconds to start
- Database migrations are run automatically via Flyway on startup
- All data is persisted in Docker volumes
- Use `docker-compose down -v` to remove volumes and start fresh
- The backend container includes health checks to ensure it's ready
