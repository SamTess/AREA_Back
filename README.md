# AREA Backend

Backend REST API for the AREA (Action REAction) application - a service for creating automated workflows between different applications.

## Features

✅ **Spring Boot Application** with Java 21  
✅ **Spring Data JPA** repositories for User, Service, and Area entities  
✅ **REST Controllers** with full CRUD operations  
✅ **PostgreSQL** database with Flyway migrations  
✅ **Unit Tests** for repositories  
✅ **Integration Tests** with Testcontainers  
✅ **OpenAPI/Swagger** documentation (configured but requires proper setup)  
✅ **Security** with BCrypt password encoding  

## Quick Start

### Prerequisites
- Java 21+
- Docker & Docker Compose
- Gradle (wrapper included)

### 1. Start the Database
```bash
docker-compose up -d postgres
```

### 2. Run the Application
```bash
./gradlew bootRun
```

### 3. Run Tests
```bash
# Run all tests
./gradlew test

# Run only repository tests
./gradlew test --tests "*.repository.*"
```

## API Endpoints

### Users API (`/api/users`)
- `GET /api/users` - Get paginated users list
- `GET /api/users/{id}` - Get user by ID
- `POST /api/users` - Create new user
- `PUT /api/users/{id}` - Update user
- `DELETE /api/users/{id}` - Delete user
- `GET /api/users/search?query={emailOrUsername}` - Search users

### Services API (`/api/services`)
- `GET /api/services` - Get paginated services list
- `GET /api/services/{id}` - Get service by ID
- `GET /api/services/enabled` - Get enabled services only
- `POST /api/services` - Create new service
- `PUT /api/services/{id}` - Update service
- `DELETE /api/services/{id}` - Delete service
- `GET /api/services/search?name={serviceName}` - Search services by name

### Areas API (`/api/areas`)
- `GET /api/areas` - Get paginated areas list
- `GET /api/areas/{id}` - Get area by ID
- `GET /api/areas/user/{userId}` - Get areas by user
- `POST /api/areas` - Create new area
- `PUT /api/areas/{id}` - Update area
- `DELETE /api/areas/{id}` - Delete area
- `PATCH /api/areas/{id}/toggle` - Toggle area enabled status
- `GET /api/areas/search?name={areaName}` - Search areas by name

## Database Schema

### Users Table
- `id` - Primary key
- `email` - Unique email address
- `username` - Unique username
- `password` - BCrypt encoded password
- `first_name`, `last_name` - User personal info
- `enabled` - Account status
- `created_at`, `updated_at` - Timestamps

### Services Table
- `id` - Primary key
- `name` - Unique service name
- `description` - Service description
- `icon_url` - Service icon URL
- `api_endpoint` - Service API endpoint
- `auth_type` - Authentication type (OAUTH2, API_KEY, BASIC_AUTH, NONE)
- `enabled` - Service status
- `created_at`, `updated_at` - Timestamps

### Areas Table
- `id` - Primary key
- `name` - Area name
- `description` - Area description
- `user_id` - Foreign key to users
- `action_service_id` - Foreign key to services (trigger)
- `action_type`, `action_config` - Action configuration
- `reaction_service_id` - Foreign key to services (reaction)
- `reaction_type`, `reaction_config` - Reaction configuration
- `enabled` - Area status
- `last_triggered` - Last trigger timestamp
- `created_at`, `updated_at` - Timestamps

## Configuration

### Development (application.properties)
```properties
# Database
spring.datasource.url=jdbc:postgresql://localhost:5432/area_db
spring.datasource.username=area_user
spring.datasource.password=area_password

# JPA
spring.jpa.hibernate.ddl-auto=update
spring.jpa.show-sql=true

# Security (Basic Auth for development)
spring.security.user.name=admin
spring.security.user.password=admin123
```

### Testing (application-test.properties)
```properties
# H2 in-memory database for tests
spring.datasource.url=jdbc:h2:mem:testdb
spring.jpa.hibernate.ddl-auto=create-drop
spring.flyway.enabled=false
```

## Authentication

Currently uses HTTP Basic Authentication:
- **Username**: `admin`
- **Password**: `admin123`

All API endpoints require authentication.

## Sample Data

The application includes sample services in the migration:
- Gmail (OAUTH2)
- GitHub (OAUTH2)  
- Slack (OAUTH2)
- Discord (OAUTH2)
- Weather API (API_KEY)

## Development

### Project Structure
```
src/main/java/area/server/AREA_Back/
├── entity/          # JPA entities
├── repository/      # Spring Data repositories
├── controller/      # REST controllers
├── dto/             # Data transfer objects
├── config/          # Configuration classes
└── AreaBackApplication.java

src/test/java/area/server/AREA_Back/
├── repository/      # Repository unit tests
└── AreaBackApplicationTests.java
```

### Building
```bash
./gradlew build
```

### Running with Docker
```bash
# Start all services
docker-compose up

# Or just the backend (after starting postgres)
./gradlew bootRun
```

## TODOs

- [ ] Implement proper OpenAPI/Swagger configuration
- [ ] Add comprehensive integration tests with Testcontainers
- [ ] Implement JWT authentication
- [ ] Add service implementations for actual integrations
- [ ] Add area triggering/execution logic
- [ ] Add API versioning
- [ ] Add comprehensive logging and monitoring
- [ ] Add Docker image for the application