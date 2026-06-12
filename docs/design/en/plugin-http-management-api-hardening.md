# Plugin HTTP Management API Semantic Hardening Design

## Background

The previous implementation completed the main HTTP management API surface, but review found several production-semantic risks:

- `/deployments/replace` computed `dryRun` but always executed the real replacement path.
- Idempotency used check-then-save, so two concurrent requests with the same key could both execute the real operation.
- Pre-execution rejection paths such as authentication, authorization, CSRF, rate limit, and parameter validation were not uniformly audited.
- Error responses could expose raw lower-level exception messages containing absolute paths, tokens, or environment details.
- `confirm` reused the `replace` permission, which is too coarse for future manual gate governance.

## Affected Modules

- `pf4boot-api`
  - Extend `PluginOperationStore` with atomic idempotency reservation.
  - Give `PluginManagementOperation.DEPLOYMENT_CONFIRM` a dedicated permission.
- `pf4boot-management-starter`
  - Fix deployment dry-run semantics.
  - Make in-memory idempotency reservation atomic.
  - Add reject-path audit and safe external response messages.
  - Add unit tests for the above behavior.

## Design

### Dry-run Semantics

`POST /deployments/replace` calls `PluginDeploymentService.planReplacement(...)` when `dryRun=true`; it mutates runtime only when `dryRun=false`. `POST /deployments/plan` is always treated as dry-run.

### Atomic Idempotency

Add `saveIfIdempotencyKeyAbsent(PluginOperationRecord record)` to `PluginOperationStore`. The in-memory store uses `ConcurrentHashMap.putIfAbsent` to reserve the idempotency key before saving the operation record, so only one request becomes the executor for a given key.

### Reject-path Audit

The controller records audit events after a `PluginManagementRequest` has been created even when write security, authentication, authorization, idempotency, or parameter validation rejects the request.

### Safe Responses

External responses use stable safe messages by error code instead of raw exception messages. Internal audit messages may still carry operational context, but must not expose tokens.

### Confirm Permission

Add `pf4boot:deployment:confirm`. Local token mode grants it by default; remote delegated mode can separate replace and confirm permissions.

## Compatibility

- `PluginOperationStore` is a new management SPI and the repository in-memory implementation is updated in lockstep.
- Remote authorizers must grant `pf4boot:deployment:confirm` to use the confirm endpoint.
- The default replace behavior now matches `dryRunDefault`; this may stop accidental real replacements that previously happened because of the bug.

## Verification

- `.\gradlew.bat :pf4boot-api:test`
- `.\gradlew.bat :pf4boot-management-starter:test`
- Add tests for dry-run replace, explicit real replace, concurrent idempotency reservation, reject audit, safe exception responses, and dedicated confirm permission.
