# System Diagrams

This section contains essential UML diagrams documenting the architecture and key workflows of the AREA system.

## Class Diagrams

Class diagrams show the structure of the system, including entities, services, and their relationships.

### [Core Entities Class Diagram](./01-core-entities-class-diagram.md)
Complete domain model showing all main entities: User, Area, Service, ActionDefinition, ActionInstance, ServiceAccount, and their relationships.

**Key Components:**
- User and authentication entities
- Area workflow structure
- Service integration model
- Action definition and instances

### [Authentication System Class Diagram](./02-authentication-class-diagram.md)
Detailed view of authentication and authorization components.

**Key Components:**
- JWT token management
- OAuth 2.0 integration
- Service account management
- Security filters and configuration

### [Service Integration Architecture Class Diagram](./03-service-integration-class-diagram.md)
Architecture for integrating external services and executing actions.

**Key Components:**
- ActionExecutor interface and implementations
- Service client abstractions
- Activation mode management
- Execution orchestration

---

## Sequence Diagrams

Sequence diagrams illustrate the dynamic behavior and interactions between components for key use cases.

### [OAuth Authentication Flow](./04-oauth-flow-sequence.md)
Complete OAuth 2.0 flow for both user authentication and service account connection.

**Covers:**
- User login with Google/GitHub
- Service account OAuth setup
- Token refresh mechanism
- Error handling and security

### [AREA Creation Flow](./05-area-creation-sequence.md)
Step-by-step process of creating an automation workflow with actions, reactions, and data links.

**Covers:**
- Request validation
- Entity creation (Area, ActionInstance, ActivationMode)
- Link establishment between actions
- Webhook registration
- Cron job scheduling

### [AREA Execution Flow](./06-area-execution-sequence.md)
How an AREA is triggered and executed from start to finish.

**Covers:**
- Webhook-triggered execution
- Cron-scheduled execution
- Manual execution
- Data mapping and transformation
- Conditional execution
- Error handling

### [Webhook System](./07-webhook-system-sequence.md)
Webhook registration, event reception, validation, and processing.

**Covers:**
- Webhook registration with external services
- Event validation and signature verification
- Webhook deregistration
- Retry mechanism
- Security features

---

## How to Read These Diagrams

### Mermaid Syntax
All diagrams use [Mermaid](https://mermaid.js.org/) syntax for easy rendering in:
- GitHub/GitLab markdown viewers
- Documentation sites (Docusaurus)
- VS Code with Mermaid extensions
- Online editors like [Mermaid Live](https://mermaid.live)

### Class Diagram Notation
- **Solid lines with arrows**: Associations and dependencies
- **Numbers (1, \*, 0..1)**: Cardinality (one-to-many, many-to-many, etc.)
- **`<<stereotype>>`**: Special component types (service, interface, enum)
- **`-`**: Private members
- **`+`**: Public members

### Sequence Diagram Notation
- **Solid arrows →**: Synchronous calls
- **Dashed arrows ⇢**: Return values
- **activate/deactivate**: Execution lifespan
- **alt/else**: Conditional flows
- **loop**: Repeated operations
- **Note**: Additional information

---

## Usage in Documentation

These diagrams are referenced throughout the technical documentation:
- [Architecture Overview](../01-architecture-overview.md)
- [Authentication & Authorization](../02-authentication-authorization.md)
- [AREA Management](../03-area-management.md)
- [Service Integration](../04-service-integration.md)
- [Webhook System](../09-webhook-system.md)

---

## Updating Diagrams

When updating the system architecture:
1. Update the relevant diagram(s) in this directory
2. Ensure the diagram reflects actual implementation
3. Update references in technical documentation
4. Sync changes to Docusaurus documentation

---

## Tools and Resources

### Recommended Editors
- [Mermaid Live Editor](https://mermaid.live) - Online editor with real-time preview
- [VS Code Mermaid Extension](https://marketplace.visualstudio.com/items?itemName=bierner.markdown-mermaid) - Preview in VS Code
- [Draw.io](https://draw.io) - For more complex diagrams

### Mermaid Documentation
- [Class Diagrams](https://mermaid.js.org/syntax/classDiagram.html)
- [Sequence Diagrams](https://mermaid.js.org/syntax/sequenceDiagram.html)
- [Styling Guide](https://mermaid.js.org/config/theming.html)
