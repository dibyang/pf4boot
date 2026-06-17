# JPA Management Starter Boundary

## Problem

Base plugin management needs only plugin queries, lifecycle operations, and deployment orchestration. Keeping JPA reload HTTP or Actuator support inside `pf4boot-management-starter` or `pf4boot-actuator` makes non-JPA applications package `pf4boot-jpa` unintentionally and makes CLIs/consoles treat `plugin-jpa-reload` as a base command.

## Affected Modules

- `pf4boot-management-starter`: keeps only base plugin management and `plugin-deploy`; it does not depend on `pf4boot-jpa`.
- `pf4boot-actuator`: keeps plugin snapshot, governance, and base metrics endpoints; it does not depend on `pf4boot-jpa`.
- `pf4boot-jpa-management-starter`: optional module that owns JPA reload HTTP APIs and the `pf4bootjpareload` Actuator endpoint.
- `samples/cross-plugin-jpa`: explicitly depends on `pf4boot-jpa-management-starter` and continues to cover JPA reload smoke tests.

## Proposed Design

The default `pf4boot-management-starter` API surface contains only:

- `GET /pf4boot/admin/plugins`
- `GET /pf4boot/admin/plugins/{pluginId}`
- `POST /pf4boot/admin/plugins/{pluginId}/start`
- `POST /pf4boot/admin/plugins/{pluginId}/stop`
- `POST /pf4boot/admin/plugins/{pluginId}/restart`
- `POST /pf4boot/admin/plugins/{pluginId}/reload`
- `POST /pf4boot/admin/plugins/{pluginId}/enable`
- `DELETE /pf4boot/admin/plugins/{pluginId}/enable`
- `GET /pf4boot/admin/deployments`
- `GET /pf4boot/admin/deployments/{deploymentId}`
- `POST /pf4boot/admin/deployments/plan`
- `POST /pf4boot/admin/deployments/replace`
- `POST /pf4boot/admin/deployments/{deploymentId}/confirm`
- `POST /pf4boot/admin/deployments/{deploymentId}/rollback`

JPA reload is registered only when the application explicitly adds `pf4boot-jpa-management-starter`:

- `POST /pf4boot/admin/jpa/domains/{domainId}/reload/plan`
- `POST /pf4boot/admin/jpa/domains/{domainId}/reload`
- `GET /pf4boot/admin/jpa/reloads/{reloadId}`
- `GET /pf4boot/admin/jpa/domains/{domainId}/reload/current`
- `/actuator/pf4bootjpareload`

`pf4boot-jpa-management-starter` reuses authorization, request factory, audit, and path validation beans from the base management module, but it owns the `pf4boot-jpa` dependency. Polarix-like non-JPA management deployments should include `pf4boot-management-starter` and optional base `pf4boot-actuator`, omit `pf4boot-jpa-management-starter`, and avoid generating a `plugin-jpa-reload` CLI command.

## Compatibility

This is a breaking dependency-boundary cleanup: applications that depend only on `pf4boot-management-starter` no longer get JPA reload HTTP APIs; applications that depend only on `pf4boot-actuator` no longer get the `pf4bootjpareload` endpoint. Applications that need JPA reload must add `pf4boot-jpa-management-starter` explicitly.

Base plugin management endpoints for plugin list/start/stop/restart/reload/enable/disable and plugin-deploy remain unchanged.

## Verification

- `.\gradlew.bat :pf4boot-management-starter:compileJava`
- `.\gradlew.bat :pf4boot-actuator:compileJava`
- `.\gradlew.bat :pf4boot-jpa-management-starter:test`
- `.\gradlew.bat :samples:cross-plugin-jpa:demo-host:compileJava`

## Open Questions

- Whether JPA reload CLI support should live in a separate extension package instead of the base CLI.
