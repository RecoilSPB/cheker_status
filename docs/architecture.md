# Architecture

Project code is grouped by feature packages under
`ru.spb.reshenie.chekerstatus`.

The current structure favors navigation by business area instead of generic
Clean Architecture layers. Spring components are injected directly when they
have a single production implementation; small interfaces remain only where
they describe a real event or integration contract.

## Package Map

```text
ru.spb.reshenie.chekerstatus
├── ChekerStatusApplication.java
├── common
│   └── json
├── config
│   ├── gitlab
│   ├── http
│   ├── nsi
│   └── security
├── gitlab
│   ├── client
│   ├── model
│   ├── parser
│   ├── repository
│   ├── service
│   └── sync
├── nsi
│   ├── client
│   ├── model
│   ├── repository
│   ├── service
│   └── scheduler
├── sync
│   ├── model
│   ├── query
│   ├── repository
│   └── service
└── web
    ├── controller
    ├── dto
    ├── model
    ├── repository
    └── service
```

## Responsibilities

- `config` contains Spring Boot configuration and property classes grouped by
  technical area: security, HTTP/WebSocket, NSI, and GitLab.
- `nsi` contains the NSI client, dictionary/passport/record models,
  persistence code, synchronization service, progress tracking, and scheduler.
- `gitlab` contains the GitLab API client, commit/file models, link parsing,
  JDBC repositories, synchronization services, executor, and concurrency
  limiter.
- `sync` contains synchronization run models, query/filter objects, the
  repository for run history, and lifecycle services. `LiveUpdatePublisher`
  lives here as a small event contract used by sync and NSI services.
- `web` contains MVC/API controllers, request/response DTOs, UI read models,
  UI query repository, time formatting helpers, and the WebSocket publisher
  implementation.
- `common.json` contains the shared JSON helper used by both NSI and GitLab
  models.

## Dependency Guidance

Feature packages may use shared `sync` services and models where they represent
cross-feature synchronization state. The `web` package should adapt that state
for HTTP, Thymeleaf, and WebSocket clients. Core synchronization code should
publish live-update events through `sync.service.LiveUpdatePublisher` rather
than depending on WebSocket implementation classes directly.

Configuration keys, HTTP endpoints, Flyway migrations, database tables, and
templates are part of the external contract and should not change during package
refactoring.
