# AREA Backend Architecture Overview

## Table of Contents
- [Introduction](#introduction)
- [System Diagrams](#system-diagrams)
- [System Architecture](#system-architecture)
- [Technology Stack](#technology-stack)
- [Project Structure](#project-structure)
- [Key Components](#key-components)
- [Data Flow](#data-flow)
- [Security Architecture](#security-architecture)
- [Database Design](#database-design)

## Introduction

The AREA Backend is a Spring Boot-based REST API that serves as the core engine for the AREA automation platform. It provides user management, service integration, automation creation, and execution capabilities through a robust microservices-oriented architecture.

## System Diagrams

For detailed visual representations of the system architecture and workflows, refer to our comprehensive [System Diagrams](./diagrams/README.md) documentation:

### Class Diagrams
- **[Core Entities](./diagrams/01-core-entities-class-diagram.md)**: Complete domain model with all main entities and relationships
- **[Authentication System](./diagrams/02-authentication-class-diagram.md)**: JWT, OAuth2, and service account management
- **[Service Integration Architecture](./diagrams/03-service-integration-class-diagram.md)**: External service integration and action execution

### Sequence Diagrams
- **[OAuth Authentication Flow](./diagrams/04-oauth-flow-sequence.md)**: User login and service account connection
- **[AREA Creation Flow](./diagrams/05-area-creation-sequence.md)**: Complete workflow creation process
- **[AREA Execution Flow](./diagrams/06-area-execution-sequence.md)**: Trigger detection to reaction completion
- **[Webhook System](./diagrams/07-webhook-system-sequence.md)**: Webhook registration, validation, and processing

These diagrams provide essential visual documentation of the system's most important components and workflows.

## System Architecture

The backend follows a layered architecture pattern with clear separation of concerns:

```
┌─────────────────────────────────────────────────────────────┐
│                    Presentation Layer                       │
│  ┌─────────────┐ ┌─────────────┐ ┌─────────────┐ ┌────────┐ │
│  │   Web API   │ │   Swagger   │ │  Actuator   │ │ OAuth  │ │
│  │ Controllers │ │     UI      │ │ Endpoints   │ │   API  │ │
│  └─────────────┘ └─────────────┘ └─────────────┘ └────────┘ │
└─────────────────────────────────────────────────────────────┘
                              │
┌─────────────────────────────────────────────────────────────┐
│                    Security Layer                           │
│  ┌─────────────┐ ┌─────────────┐ ┌─────────────┐           │
│  │  JWT Auth   │ │    CORS     │ │   Spring    │           │
│  │   Filter    │ │ Configuration│ │  Security   │           │
│  └─────────────┘ └─────────────┘ └─────────────┘           │
└─────────────────────────────────────────────────────────────┘
                              │
┌─────────────────────────────────────────────────────────────┐
│                   Business Logic Layer                      │
│  ┌─────────────┐ ┌─────────────┐ ┌─────────────┐ ┌────────┐ │
│  │    Area     │ │    Auth     │ │   Service   │ │ Worker │ │
│  │  Services   │ │  Services   │ │  Services   │ │ Module │ │
│  └─────────────┘ └─────────────┘ └─────────────┘ └────────┘ │
└─────────────────────────────────────────────────────────────┘
                              │
┌─────────────────────────────────────────────────────────────┐
│                    Data Access Layer                        │
│  ┌─────────────┐ ┌─────────────┐ ┌─────────────┐           │
│  │    JPA      │ │    Redis    │ │   Flyway    │           │
│  │ Repositories│ │   Cache     │ │ Migrations  │           │
│  └─────────────┘ └─────────────┘ └─────────────┘           │
└─────────────────────────────────────────────────────────────┘
                              │
┌─────────────────────────────────────────────────────────────┐
│                     Data Layer                              │
│  ┌─────────────┐ ┌─────────────┐                           │
│  │ PostgreSQL  │ │    Redis    │                           │
│  │  Database   │ │   Server    │                           │
│  └─────────────┘ └─────────────┘                           │
└─────────────────────────────────────────────────────────────┘
```

## Technology Stack

### Core Framework
- **Spring Boot 3.5.6**: Main application framework
- **Java 21**: Programming language
- **Gradle**: Build and dependency management

### Database & Persistence
- **PostgreSQL 13+**: Primary database
- **Redis**: Caching and session storage
- **Spring Data JPA**: ORM framework
- **Flyway**: Database migration management

### Security
- **Spring Security**: Security framework
- **JWT**: Token-based authentication
- **OAuth2**: Third-party authentication (GitHub, Google)
- **BCrypt**: Password hashing

### API & Documentation
- **Spring Web**: REST API framework
- **OpenAPI 3 (Swagger)**: API documentation
- **Jackson**: JSON serialization

### Monitoring & Observability
- **Spring Actuator**: Application monitoring
- **Prometheus**: Metrics collection
- **SLF4J + Logback**: Logging

### Testing
- **JUnit 5**: Unit testing framework
- **Testcontainers**: Integration testing
- **Mockito**: Mocking framework

## Project Structure

```
src/
├── main/
│   ├── java/area/server/AREA_Back/
│   │   ├── AreaBackApplication.java          # Main application class
│   │   ├── config/                           # Configuration classes
│   │   │   ├── SecurityConfig.java           # Security configuration
│   │   │   ├── DatabaseConfig.java           # Database configuration
│   │   │   ├── RedisConfig.java              # Redis configuration
│   │   │   ├── OpenApiConfig.java            # Swagger configuration
│   │   │   └── AsyncConfig.java              # Async configuration
│   │   ├── controller/                       # REST controllers
│   │   │   ├── AreaController.java           # AREA management
│   │   │   ├── AuthController.java           # Authentication
│   │   │   ├── OAuthController.java          # OAuth integration
│   │   │   ├── ServiceController.java        # Service management
│   │   │   └── AboutController.java          # System information
│   │   ├── service/                          # Business logic services
│   │   │   ├── AreaService.java              # AREA operations
│   │   │   ├── AuthService.java              # Authentication logic
│   │   │   ├── EmailService.java             # Email sending service
│   │   │   ├── JwtService.java               # JWT operations
│   │   │   ├── OAuthService.java             # OAuth base service
│   │   │   ├── RedisEventService.java        # Event processing
│   │   │   └── ExecutionService.java         # Automation execution
│   │   ├── entity/                           # JPA entities
│   │   │   ├── User.java                     # User entity
│   │   │   ├── Area.java                     # AREA entity
│   │   │   ├── Service.java                  # Service entity
│   │   │   ├── ActionDefinition.java         # Action definition
│   │   │   └── ActionInstance.java           # Action instance
│   │   ├── repository/                       # Data repositories
│   │   │   ├── UserRepository.java           # User data access
│   │   │   ├── AreaRepository.java           # AREA data access
│   │   │   └── ServiceRepository.java        # Service data access
│   │   ├── dto/                              # Data Transfer Objects
│   │   │   ├── AreaResponse.java             # AREA response DTO
│   │   │   ├── CreateAreaRequest.java        # AREA creation DTO
│   │   │   └── AuthResponse.java             # Authentication response
│   │   ├── filter/                           # Security filters
│   │   │   └── JwtAuthenticationFilter.java  # JWT authentication
│   │   ├── worker/                           # Background workers
│   │   │   ├── AreaReactionWorker.java       # Reaction processor
│   │   │   └── ReactionExecutor.java         # Execution engine
│   │   └── util/                             # Utility classes
│   └── resources/
│       ├── application.properties            # Application configuration
│       └── db/migration/                     # Database migrations
└── test/                                     # Test classes
    ├── java/area/server/AREA_Back/
    │   ├── controller/                       # Controller tests
    │   ├── service/                          # Service tests
    │   ├── repository/                       # Repository tests
    │   └── config/                           # Test configurations
    └── resources/                            # Test resources
```

## Key Components

### 1. Controllers Layer
- **REST API endpoints** for client communication
- **Request validation** and error handling
- **OpenAPI documentation** with Swagger annotations
- **Security integration** with Spring Security

### 2. Service Layer
- **Business logic implementation**
- **Transaction management**
- **Integration with external services**
- **Data validation and processing**

### 3. Repository Layer
- **Data access abstraction**
- **JPA entity management**
- **Custom query implementations**
- **Database transaction handling**

### 4. Security Layer
- **JWT-based authentication**
- **OAuth2 integration** (GitHub, Google)
- **CORS configuration**
- **Access control and authorization**

### 5. Worker Module
- **Asynchronous task processing**
- **Event-driven architecture**
- **Redis stream processing**
- **Reaction execution engine**

## Data Flow

### 1. User Authentication Flow
```
Client → AuthController → AuthService → JwtService → RedisTokenService → Database
                                     ↓
                               JWT Cookies ← Response
```

### 2. Email Verification Flow
```
Client → AuthController → AuthService → EmailService → SMTP/Resend API
     ↓                        ↓
Email Sent ← Verification Token Stored in Database
     ↓
Client → AuthController → AuthService → Database → Account Activated
```

### 3. Password Reset Flow
```
Client → AuthController → AuthService → EmailService → SMTP/Resend API
     ↓                        ↓
Email Sent ← Reset Token Stored in Database
     ↓
Client → AuthController → AuthService → Database → Password Updated
```

### 4. AREA Creation Flow
```
Client → AreaController → AreaService → JsonSchemaValidationService
                                    ↓
                              ActionInstanceRepository → Database
                                    ↓
                            CronSchedulerService → Background Workers
```

### 5. Automation Execution Flow
```
External Trigger → WebhookController → RedisEventService → Redis Stream
                                                              ↓
                        AreaReactionWorker ← Redis Consumer Group
                                ↓
                     ReactionExecutor → External Services
```

## Security Architecture

### Authentication & Authorization
- **JWT tokens** stored in HTTP-only cookies
- **Access tokens** (15 minutes) and **refresh tokens** (7 days)
- **Redis-based token validation** and blacklisting
- **OAuth2 integration** for social login
- **Email verification** for account activation
- **Secure password reset** with time-limited tokens

### Data Protection
- **BCrypt password hashing**
- **CORS configuration** for cross-origin requests
- **HTTPS enforcement** in production
- **SQL injection prevention** through JPA

### API Security
- **Rate limiting** (planned)
- **Input validation** with Bean Validation
- **Error handling** without information leakage
- **Actuator endpoints** protection

## Database Design

### Core Tables
- **a_users**: User accounts and profiles
- **a_areas**: Automation definitions
- **a_services**: External service configurations
- **a_action_definitions**: Available actions/reactions
- **a_action_instances**: Configured action instances
- **a_executions**: Execution history and status

### Security Tables
- **a_user_local_identities**: Local authentication data
- **a_user_oauth_identities**: OAuth provider data
- **a_service_accounts**: Service authentication tokens

### Features
- **JSONB columns** for flexible schema evolution
- **UUID primary keys** for scalability
- **Database migrations** with Flyway
- **Audit trails** with timestamps
- **Database indexes** for query optimization

## Deployment Architecture

The application is designed for containerized deployment with:
- **Docker support** with multi-stage builds
- **Environment-based configuration**
- **Health checks** via Spring Actuator
- **Metrics collection** for Prometheus
- **Horizontal scaling** capabilities

## Next Steps

For detailed information about specific components, see:
- [Authentication & Authorization](./02-authentication-authorization.md)
- [Area Management System](./03-area-management.md)
- [Service Integration](./04-service-integration.md)
- [Database Schema](./05-database-schema.md)
- [API Documentation](./06-api-documentation.md)
- [Security Guide](./07-security-guide.md)
