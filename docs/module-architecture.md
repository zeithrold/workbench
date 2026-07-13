# Module architecture

Workbench is a responsibility-oriented modular monolith. `./gradlew moduleArchitectureCheck`
enforces the production dependency graph, package ownership, domain technology boundaries, and
the ban on repository-wide component scanning.

| Module | Owns | Allowed production dependencies |
| --- | --- | --- |
| `workbench-kernel` | IDs, errors, pagination, warnings, event envelope/codec, lock and blob ports | none |
| `workbench-tenant` | tenant lifecycle, tenant configuration, tenant events and ports | kernel |
| `workbench-identity` | accounts, members, invitations, permissions, authentication domain and ports | kernel, tenant |
| `workbench-agile` | projects, sprints, work items, query AST, templates, rich text and events | kernel, identity |
| `workbench-notification` | notifications, preferences, ports and notification service | kernel |
| `workbench-application` | synchronous cross-domain use cases, event handlers and Outbox execution | kernel and domain modules |
| `workbench-data` | Exposed/Flyway, storage, mail and messaging output adapters | kernel, application and domain modules |
| `workbench-security` | Spring Security and authentication protocol adapters | kernel, identity, tenant |
| `workbench-web` | HTTP/OpenAPI adapters and the Web composition root | all required library modules |
| `workbench-worker` | Kafka input adapter and the Worker composition root | kernel, application, data |

```text
agile -> identity -> tenant -> kernel
notification ----------------> kernel
application -> agile, identity, tenant, notification, kernel
data -> application and port-owning domain modules
security -> identity, tenant, kernel
web -> application, data, security
worker -> application, data
```

Domain modules may use Spring Context for bean declaration, but not Web/Servlet, Security, Mail,
Kafka, Redis, or Exposed APIs. Kernel is Spring-free. Every bean-bearing library exposes a scoped
module configuration; Web and Worker import their composition explicitly.

Migration status: complete. The former `workbench-core`, `workbench-service`, and `workbench-jobs`
modules and packages have no compatibility layer.
