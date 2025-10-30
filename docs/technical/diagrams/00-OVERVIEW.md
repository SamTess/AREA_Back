# AREA System - UML Diagrams Overview

This document provides an overview of all UML diagrams created for the AREA (Action REAction) system documentation.

## Purpose

These diagrams fulfill the project requirement to **include essential diagrams like class and sequence diagrams for the most important system components**. They provide comprehensive visual documentation of the system architecture, data models, and key workflows.

## Diagram Categories

### 📊 Class Diagrams (3 diagrams)

Class diagrams illustrate the static structure of the system, showing entities, services, and their relationships.

1. **[Core Entities Class Diagram](./01-core-entities-class-diagram.md)**
   - Complete domain model
   - All main entities: User, Area, Service, ActionDefinition, ActionInstance, ServiceAccount
   - Entity relationships and cardinalities
   - Key enumerations (AuthType, ActivationModeType, DedupStrategy)

2. **[Authentication System Class Diagram](./02-authentication-class-diagram.md)**
   - Authentication and authorization components
   - JWT token management (JwtService)
   - OAuth 2.0 integration (OAuthService)
   - Service account management (ServiceAccountService)
   - Security configuration and filters

3. **[Service Integration Architecture Class Diagram](./03-service-integration-class-diagram.md)**
   - External service integration pattern
   - ActionExecutor interface and implementations (GitHub, Gmail, Slack)
   - Execution orchestration (AreaExecutionService)
   - Activation modes (WebhookHandler, CronScheduler, PollingService)

### 🔄 Sequence Diagrams (4 diagrams)

Sequence diagrams show the dynamic behavior and interactions between components for critical use cases.

4. **[OAuth Authentication Flow Sequence Diagram](./04-oauth-flow-sequence.md)**
   - User authentication with OAuth providers (Google, GitHub)
   - Service account connection flow
   - Token refresh mechanism
   - Error handling and security measures

5. **[AREA Creation Flow Sequence Diagram](./05-area-creation-sequence.md)**
   - Complete workflow creation process
   - Validation phase
   - Entity creation (Area, ActionInstance, ActivationMode)
   - Link establishment between actions
   - Webhook registration and cron scheduling

6. **[AREA Execution Flow Sequence Diagram](./06-area-execution-sequence.md)**
   - Webhook-triggered execution
   - Cron-scheduled execution
   - Manual execution
   - Data mapping and transformation
   - Conditional execution logic
   - Error handling

7. **[Webhook System Sequence Diagram](./07-webhook-system-sequence.md)**
   - Webhook registration with external services
   - Event reception and signature validation
   - Webhook processing pipeline
   - Deregistration flow
   - Retry mechanism

## Diagram Technology

All diagrams are created using **Mermaid** syntax, which offers:

- ✅ **Version Control**: Text-based format works perfectly with Git
- ✅ **Easy Rendering**: Renders in GitHub, GitLab, Docusaurus, VS Code
- ✅ **Maintainability**: Simple to update and modify
- ✅ **No Special Tools**: Can be edited in any text editor
- ✅ **Portable**: Works across different platforms and documentation systems

## Coverage of System Components

### ✅ Entities & Data Models
- User, Area, Service
- ActionDefinition, ActionInstance
- ServiceAccount, UserOAuthIdentity
- ActivationMode, ActionLink

### ✅ Services & Business Logic
- Authentication services (AuthService, OAuthService, JwtService)
- AREA management (AreaService, AreaExecutionService)
- Service integration (ActionExecutor implementations)
- Webhook management (WebhookHandler, WebhookRegistry)
- Scheduling (CronScheduler, PollingService)

### ✅ Security Components
- JWT authentication filter
- OAuth 2.0 flows
- Token management and refresh
- Signature validation

### ✅ Key Workflows
- User registration and login
- Service account connection
- AREA creation with actions and reactions
- AREA execution (webhook, cron, manual)
- Webhook lifecycle (registration, processing, deregistration)

## How to View the Diagrams

### In GitHub/GitLab
Diagrams render automatically when viewing the markdown files in the repository.

### In Docusaurus
Navigate to **Technical Documentation → System Diagrams** in the documentation site.

### In VS Code
Install the [Markdown Preview Mermaid Support](https://marketplace.visualstudio.com/items?itemName=bierner.markdown-mermaid) extension.

### Online
Use [Mermaid Live Editor](https://mermaid.live) to view and edit diagrams interactively.

## Documentation Integration

These diagrams are integrated into:

1. **Main README** - Quick reference in the documentation section
2. **Architecture Overview** - Linked from the technical documentation
3. **Specific Technical Docs** - Referenced in relevant sections:
   - Authentication diagrams in `02-authentication-authorization.md`
   - AREA management diagrams in `03-area-management.md`
   - Service integration diagrams in `04-service-integration.md`
   - Webhook diagrams in `09-webhook-system.md`

## Maintenance

When updating the system:

1. **Code Changes**: If entity structure changes, update class diagrams
2. **New Workflows**: Add sequence diagrams for new critical flows
3. **Service Integration**: Update service integration diagrams when adding new services
4. **Keep Sync**: Ensure diagrams reflect actual implementation

## Summary

This comprehensive set of **7 UML diagrams** (3 class + 4 sequence) provides:

- 📐 **Complete architectural overview** of the AREA system
- 🔍 **Detailed component relationships** and data models
- 🚀 **Critical workflow documentation** for key use cases
- 🛡️ **Security flow visualization** for authentication and authorization
- 🔗 **Service integration patterns** for extensibility

These diagrams serve as essential documentation for developers, architects, and stakeholders to understand the AREA system's design and operation.

---

**Location**: `/docs/technical/diagrams/` and `/docusaurus/docs/technical/diagrams/`  
**Format**: Mermaid (Markdown)  
**Maintained by**: AREA Development Team  
**Last Updated**: October 30, 2025
