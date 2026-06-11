# Plugin Hot Replacement Deployment Acceptance Record

## 1. Acceptance Scope

This record tracks acceptance results for plugin hot replacement deployment improvements from M1 to M7. The accepted phase-one capability is safe replacement with controlled short downtime:

- precheck generates a deployment plan without mutating runtime state;
- impact scope is calculated from the dependency graph;
- target plugin and dependents go through drain, stop, unload/load, start, and health check;
- load, start, health-check, and cleanup-validation failures roll back automatically;
- web, scheduled tasks, shared beans, and JPA resources participate in drain, cleanup validation, and health checks;
- documents describe the boundary between lifecycle primitives and deployment orchestration.

Out of scope:

- strict zero-downtime switching;
- JTA/XA or cross-datasource transactions;
- framework-owned atomic active/staged/backup/failed directory switching;
- management-plane hardening.

## 2. Acceptance Checklist

| ID | Item | Evidence | Status |
| --- | --- | --- | --- |
| AC-01 | precheck does not mutate runtime | `DefaultPluginDeploymentServiceTest.precheckDoesNotMutateRuntimeState` | Passed |
| AC-02 | impact scope is accurate | `DefaultPluginDeploymentServiceTest.planReplacementCalculatesImpactScopeAndOrders` | Passed |
| AC-03 | stop order is correct | `DefaultPluginDeploymentServiceTest.replaceStopsUnloadsLoadsAndStartsInOrder` | Passed |
| AC-04 | start order is correct | `DefaultPluginDeploymentServiceTest.replaceStopsUnloadsLoadsAndStartsInOrder` | Passed |
| AC-05 | failed new package rolls back | `replaceRollsBackWhenNewPluginStartFails`, `replaceRollsBackWhenPackageActivationFails`, `replaceRollsBackWhenPluginHealthProbeFails` | Passed |
| AC-06 | rollback failure is diagnosable | `DefaultPluginDeploymentServiceTest.replaceMovesToManualInterventionWhenRollbackFails` | Passed |
| AC-07 | web drain works | `PluginRequestMappingHandlerMappingTest` covers draining 503 and in-flight counts | Passed |
| AC-08 | in-flight requests can drain | `DefaultPluginDeploymentServiceTest.replaceRollsBackWhenDrainTimeouts` | Passed |
| AC-09 | cleanup is verifiable | `replaceRollsBackWhenCleanupVerificationFails` plus web/core/JPA cleanup verifier tests | Passed |
| AC-10 | JPA provider order is safe | `replaceJpaProviderStopsConsumersBeforeProviderAndStartsProviderFirst`, `replaceJpaProviderRollsBackWhenHealthVerifierFails` | Passed |
| AC-11 | health check is extensible | `replaceRunsPluginHealthProbeAndSucceeds`, `replaceRollsBackWhenPluginHealthProbeFails` | Passed |
| AC-12 | old APIs remain compatible | Hot replacement adds `PluginDeploymentService` without changing public `start/stop/reload/delete` semantics; existing compile/test verification passes | Passed |

Notes:

- Phase-one package activation loads the staged path. Physical directory moves and failed-package archival remain owned by external operations flows.

## 3. Recommended Verification Commands

Core lifecycle, deployment orchestration, and JPA constraints:

```powershell
.\gradlew.bat :pf4boot-api:compileJava :pf4boot-core:test :pf4boot-jpa-starter:test :pf4boot-jpa-domain-starter:test :pf4boot-starter:compileJava
```

Web drain and cleanup validation:

```powershell
.\gradlew.bat :pf4boot-web-starter:test :pf4boot-starter:compileJava
```

Complex sample plugin packaging:

```powershell
.\gradlew.bat :samples:cross-plugin-jpa:demo-host:assembleSamplePlugins
```

Runnable distribution:

```powershell
.\gradlew.bat :samples:cross-plugin-jpa:app-run:sampleDistZip
```

## 4. Manual Smoke Script

Phase one has no management API/CLI, so manual smoke uses the sample host and test entrypoints:

1. Build sample plugin packages: `.\gradlew.bat :samples:cross-plugin-jpa:demo-host:assembleSamplePlugins`.
2. Start the sample host: `.\gradlew.bat :samples:cross-plugin-jpa:demo-host:runSampleHost`.
3. Call the sample workflow endpoint and verify the old provider, service, and workflow plugins work.
4. Prepare a staged candidate package with the same plugin id.
5. Call `PluginDeploymentService.planReplacement(...)` from the host or a temporary test entrypoint and inspect stop/start order.
6. Call `PluginDeploymentService.replace(...)` and observe `DRAINING`, `STOPPING`, `CLEANUP_VERIFYING`, `STARTING`, `VERIFYING`, and `SUCCEEDED` in the deployment record.
7. Repeat with a faulty candidate and verify `ROLLING_BACK` restores the old version.

## 5. Production Constraints

- Release entrypoints should use `PluginDeploymentService.replace(...)`; do not wrap `reloadPlugin` as safe hot replacement.
- Database schema changes must use expand/contract. Do not run Contract operations during the rollback window if they break old-version compatibility.
- `spring.jpa.hibernate.ddl-auto=update` is not a production schema migration mechanism.
- Plugins using multiple datasources must split entity and repository packages by datasource and configure explicit scan paths for each datasource. Cross-datasource transactions are not supported.
- If operations require active/staged/backup/failed archival, external tooling must guarantee complete staged package writes and retain failed candidates.

## 6. Phase Conclusion

Phase-one hot replacement deployment now has a reviewable loop: design, implementation plan, module documents, acceptance checklist, core tests, and complex sample packaging evidence align. Remaining production hardening is mainly framework-owned package directory activation, persistent deployment records, and management API/CLI.
