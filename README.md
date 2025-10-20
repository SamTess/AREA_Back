# AREA Backend

A Spring Boot backend application for the AREA project (Action REAction), providing a platform to connect different services and automate workflows.

## Table of Contents

- [Overview](#overview)
- [Prerequisites](#prerequisites)
- [Installation](#installation)
- [Running the Application](#running-the-application)
- [Project Structure](#project-structure)
- [API Documentation](#api-documentation)
- [Testing](#testing)
- [Code Quality](#code-quality)
- [Database](#database)
- [Contributing](#contributing)
- [Documentation](#documentation)

## Overview

AREA Backend is a microservice-based application built with Spring Boot that allows users to create automated workflows by connecting different services. The application provides REST APIs for managing users, services, and areas (automated workflows).

### Key Features

- **User Management**: Registration, authentication, and email verification
- **Service Integration**: Connect with external services (GitHub, Slack, etc.)
- **Workflow Automation**: Create custom automation rules (AREAs)
- **Email Notifications**: SMTP and Resend API support for notifications
- **Security**: JWT authentication, OAuth2 integration, and secure password reset
- **API Documentation**: Interactive OpenAPI/Swagger documentation

## Prerequisites

Before running this application, make sure you have the following installed:

- **Java 21** or higher
- **Docker** and **Docker Compose**
- **Git**
- **Gradle** (wrapper included in the project)

### System Requirements

- **Memory**: Minimum 2GB RAM
- **Storage**: Minimum 1GB free space
- **Network**: Internet connection for downloading dependencies

## Installation

1. **Clone the repository:**
   ```bash
   git clone <repository-url>
   cd AREA_Back
   ```

2. **Set up environment variables:**
   Create a `.env` file in the root directory with the necessary environment variables:
   ```bash
   # Database Configuration
   DB_HOST=localhost
   DB_PORT=5432
   DB_NAME=area_db
   DB_USERNAME=area_user
   DB_PASSWORD=area_password
   
   # Redis Configuration
   REDIS_HOST=localhost
   REDIS_PORT=6379
   
   # Application Configuration
   SPRING_PROFILES_ACTIVE=dev
   ```

3. **Start the infrastructure services:**


## Running the Application

```bash
docker compose --env-file .env -f Docker/docker-compose.back.yaml up --build -d
```

The application will be available at: `http://localhost:8080`

## Project Structure

```
AREA_Back/
├── src/
│   ├── main/
│   │   ├── java/area/server/AREA_Back/
│   │   │   ├── config/          # Configuration classes
│   │   │   ├── controller/      # REST controllers
│   │   │   ├── dto/            # Data Transfer Objects
│   │   │   ├── entity/         # JPA entities
│   │   │   ├── repository/     # Data repositories
│   │   │   └── service/        # Business logic services
│   │   └── resources/
│   │       ├── db/migration/   # Flyway database migrations
│   │       └── application.yml # Application configuration
│   └── test/                   # Test classes
├── config/
│   └── checkstyle/            # Checkstyle configuration
├── docs/                      # Project documentation
├── build.gradle              # Gradle build configuration
├── compose.yaml              # Docker Compose configuration
└── README.md                 # This file
```

## API Documentation

The application provides interactive API documentation using OpenAPI/Swagger.

- **Swagger UI**: `http://localhost:8080/swagger-ui.html`
- **OpenAPI JSON**: `http://localhost:8080/v3/api-docs`

### Main Endpoints

- **Users**: `/api/users` - User management
- **Services**: `/api/services` - Service management
- **Areas**: `/api/areas` - Workflow management
- **About**: `/about.json` - Application information

## Testing

### Running Tests

```bash
# Run all tests
./gradlew test

# Run tests with coverage report
./gradlew jacocoTestReport

# View coverage report
open build/jacocoHtml/index.html
```

### Test Coverage

The project aims for at least 80% test coverage. Coverage reports are generated in:
- **HTML**: `build/jacocoHtml/index.html`
- **XML**: `build/reports/jacoco/test/jacocoTestReport.xml`

### Test Technologies

- **JUnit 5**: Main testing framework
- **Mockito**: Mocking framework
- **Spring Boot Test**: Integration testing
- **Testcontainers**: Database testing with real PostgreSQL
- **H2**: In-memory database for unit tests

## Code Quality

### Checkstyle

Code style is enforced using Checkstyle:

```bash
# Run checkstyle
./gradlew checkstyleMain checkstyleTest

# View checkstyle reports
open build/reports/checkstyle/main.html
open build/reports/checkstyle/test.html
```

### Quality Gates

- **Test Coverage**: Minimum 80%
- **Checkstyle**: Maximum 50 warnings, 20 errors
- **Build**: Must pass all tests and quality checks

## Database

### Database Schema

The application uses PostgreSQL with Flyway for database migrations.

- **Database**: PostgreSQL 15
- **Migration Tool**: Flyway
- **ORM**: Spring Data JPA with Hibernate

### Database Access

- **Host**: localhost:5432 (when using Docker Compose)
- **Database**: area_db
- **User**: area_user
- **Password**: area_password

### Migrations

Database migrations are located in `src/main/resources/db/migration/` and are automatically applied on application startup.

## Contributing

1. Fork the repository
2. Create a feature branch
3. Write tests for your changes
4. Ensure all tests pass and code quality checks pass
5. Submit a pull request

### Code Style

- Follow the project's Checkstyle rules
- Write comprehensive tests
- Use meaningful commit messages
- Document your code appropriately

## Documentation

Detailed documentation is available in the `docs/` directory:

- [API Documentation](docs/technical/06-api-documentation.md) - Complete API reference
- [User Email Verification Guide](docs/user-email-verification-guide.md) - Email verification and password reset
- [Email Configuration Guide](docs/email-configuration-guide.md) - SMTP and Resend setup
- [Unit Testing Guide](docs/unit-testing-guide.md) - Testing best practices
- [Checkstyle Guide](docs/checkstyle-guide.md) - Code style standards
- [Data Migration Guide](docs/data-migration-guide.md) - Database migrations
- [Services Cache Implementation](docs/services-cache-implementation.md) - Caching strategy

## Dependencies

### Main Dependencies

- **Spring Boot 3.5.6**: Main framework
- **Spring Security**: Authentication and authorization
- **Spring Data JPA**: Database abstraction
- **Spring Data Redis**: Caching
- **PostgreSQL**: Database driver
- **Flyway**: Database migrations
- **OpenAPI**: API documentation
- **Lombok**: Code generation

### Development Dependencies

- **Spring Boot DevTools**: Hot reload
- **H2**: In-memory database for testing
- **Testcontainers**: Integration testing
- **JaCoCo**: Code coverage
- **Checkstyle**: Code style checking

## License

This project is licensed under the terms specified in the LICENSE file.

## Support

For support and questions, please refer to the project documentation or contact the development team.