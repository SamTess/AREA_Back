---
sidebar_position: 1
slug: /
---

# AREA Backend Documentation

Welcome to the **AREA Backend Documentation**! This comprehensive guide will help you understand, deploy, and work with the AREA (Action REAction) backend application.

## What is AREA?

AREA Backend is a **Spring Boot microservice** that powers the AREA platform - an automation system that connects different services and creates automated workflows. Think of it as your personal automation hub, similar to IFTTT or Zapier, but self-hosted and customizable.

### Key Features

- 🔐 **User Management**: Complete authentication system with email verification
- 🔌 **Service Integration**: Connect with external services (GitHub, Slack, Discord, Google, and more)
- ⚡ **Workflow Automation**: Create custom automation rules (AREAs) with actions and reactions
- 📧 **Email Notifications**: SMTP and Resend API support for notifications
- 🔒 **Security**: JWT authentication, OAuth2 integration, and secure password reset
- 📚 **API Documentation**: Interactive OpenAPI/Swagger documentation
- 🚀 **Worker System**: Asynchronous processing for reactions and webhooks
- 💾 **Caching**: Redis integration for improved performance

## Quick Start

Get started with AREA Backend in minutes:

```bash
# Clone the repository
git clone https://github.com/YourOrg/AREA_Back.git
cd AREA_Back

# Run with Docker Compose
docker compose -f Docker/docker-compose.back.yaml up --build -d
```

Access the API at `http://localhost:8080` and Swagger UI at `http://localhost:8080/swagger-ui.html`.

## Technology Stack

- **Framework**: Spring Boot 3.x
- **Language**: Java 21
- **Database**: PostgreSQL
- **Cache**: Redis
- **Build Tool**: Gradle
- **Containerization**: Docker & Docker Compose
- **Authentication**: JWT, OAuth2
- **API Documentation**: OpenAPI 3.0 (Swagger)

## Documentation Structure

This documentation is organized into several sections:

### 📘 Guides
Step-by-step guides for common tasks:
- Database migrations
- Email configuration
- Unit testing
- Service caching
- And more...

### 🔧 Technical Documentation
Deep dives into the system architecture:
- Architecture overview
- Authentication & authorization
- AREA management
- Service integration
- Database schema
- API documentation
- Security guide
- Redis implementation
- Webhook system

### 🔌 Providers
Integration guides for external services:
- GitHub
- Slack
- Discord
- Google

### ⚙️ Worker System
Documentation for the asynchronous worker:
- Worker architecture
- Area Reaction Worker
- Quick start guide

## Prerequisites

Before you begin, ensure you have:

- **Java 21** or higher
- **Docker** and **Docker Compose**
- **Git**
- **Gradle** (wrapper included)

### System Requirements

- **Memory**: Minimum 2GB RAM
- **Storage**: Minimum 1GB free space
- **Network**: Internet connection for dependencies

## Getting Help

If you need assistance:

1. Check the relevant guide in the documentation
2. Review the technical documentation for deeper insights
3. Consult the API documentation at `/swagger-ui.html`
4. Check the project's GitHub issues

## Contributing

We welcome contributions! Please read our contributing guidelines in the repository.

## License

This project is licensed under the terms specified in the LICENSE file.

---

Ready to get started? Head over to the [Guides](/category/guides) section!
