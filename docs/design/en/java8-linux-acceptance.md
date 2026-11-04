# Java 8 and Linux Runtime Acceptance

[中文](../java8-linux-acceptance.md)

## Scope and Approach

- Build/run with the user-specified Windows JDK 8u431 and existing OpenJDK 8u312 on the authorized Linux host. Do not replace system Java or deploy into existing services.
- Reuse RuntimeSmokeRunner, existing JUnit tests and two unchanged old-plugin baselines. Standard-library scripts only export isolated classpaths, verify artifact SHA-256, launch isolated processes and collect results. Add no framework dependency or lifecycle component.
- Export runtime artifacts, test classes/resources/dependencies and baseline plugins only, excluding .git, SSH configuration, credentials and user homes. Use a dedicated Linux /tmp directory, loopback-only ports and terminate only owned processes.
- Run the same sealed bundle on real Windows/Linux Java 8: per-module tests, ordinary/production/Gson business and failure cleanup, 20 restarts, 10 reloads/collected loaders and four old-plugin upgrade runs.
- Preserve public APIs, plugin format and defaults. Once acceptance passes, stage only this thread's accumulated cleanup repairs and acceptance files, commit locally and push; do not publish or deploy.

## Execution

```powershell
.\gradlew.bat '-Dorg.gradle.java.home=C:\Program Files\Java\jdk-1.8' build :samples:cross-plugin-jpa:app-run:runtimeSmoke :samples:cross-plugin-jpa:app-run:runtimeSmokeProduction
.\gradlew.bat '-Dorg.gradle.java.home=C:\Program Files\Java\jdk-1.8' -I samples/cross-plugin-jpa/app-run/src/smoke/acceptance/export.init.gradle exportJvmAcceptance
```

The output `build/jvm-acceptance` must not exist beforehand. Run its `verify.py --seal`, transfer the whole sealed bundle, then run `python3 verify.py --java /usr/bin/java --port 17811`. On Windows run the same script with the explicit Java 8 java.exe path.

## Results

The latest appended-release-hook repair passes 366 JUnit tests, three hosts and four old-binary upgrades on each OS. See [appended hook acceptance](plugin-resource-cleanup.md#appended-hook-acceptance) for its hashes, reports and compatibility correction. The table below preserves the preceding `4ef8284` acceptance as historical evidence.

2026-11-04: the final `build/jvm-acceptance-complete` bundle passes on both platforms. This record is committed with this thread's cleanup repairs; Git history identifies the commit.

| Platform | Actual JVM | Module tests | Real hosts | Old-plugin upgrades |
| --- | --- | --- | --- | --- |
| Windows 11 x86_64 | Oracle Java 8u431 | 9 modules / 364 passing tests | Ordinary, production, Gson all pass | Both baselines in both profiles pass |
| Linux 4.18 x86_64 | OpenJDK 8u312 | 9 modules / 364 passing tests | Ordinary, production, Gson all pass | Both baselines in both profiles pass |

- Each real host covers 31 DTO GET/POST roundtrips, 20 restarts, 10 reloads and ten collected loaders, plus drain, startup/destruction retry, cross-plugin dispatch, scheduling rollback, unload/shutdown, JPA and Actuator boundaries. Each old-binary run covers business calls, 20 restarts, 10 reloads/collected loaders and failure recovery.
- Unchanged `41a04ab` and `81526a7` baselines each contain four preserved ZIPs/sidecars. Old plugins are not recompiled. Both platforms pass all sixteen checks (nine modules, three hosts, four upgrades); JUnit totals match the completed Gradle build.
- Windows builds/tests with the actual JDK 8. Linux runs exactly those compiled artifacts/dependencies; no claim is made of resolving dependencies or building sources on Linux.
- Final archive SHA-256: `620c7f275a28014a0c846e8a36903fe6fcb5bfa7231d2bf6e5fb5a7f4071c6d3`. Both input manifests share SHA-256 `9ee1f66353c53bdc63d1253916a50f32272dace7d31c242766660c77347d580a`. All 848 sealed inputs remain unchanged after execution. Acceptance port 17816 is released on both platforms.
- Windows report: `build/jvm-acceptance-complete/reports/result.json`; retrieved Linux report: `build/linux-java8-evidence/reports/result.json`. Corresponding reports directories hold JUnit logs, host JSON/XML and old-plugin logs. Build outputs/reports are not committed.

## Repairs in This Run

No new framework runtime mechanism is introduced for platform differences. Actual acceptance revealed the following test/export issues; failed candidates/local logs are retained rather than explained away by a passing retry:

1. Concurrent shutdown must not assert an instantaneous JVM Thread.State. Require Future timeout while the close barrier is held and explicit activation rejection afterward, retaining mutual-exclusion protection.
2. Host readiness must wait for both workflow and unrelated endpoints rather than treating asynchronous startup's 404 as failure.
3. Loopback Python requests explicitly bypass proxies. Windows system proxy did not bypass 127.0.0.1, preventing old-host checks from reaching the server. System proxy settings stay unchanged.
4. Application file logs identified extension test-port rebinding contention during reload. New fixtures prevent random sockets taking the extension's fixed port. Stress tests directly verify the old socket is closed rather than assuming a port number can be reused, while retaining real reload/loader collection/failure assertions. Old ZIPs remain unchanged.
5. Immutable exports must include the production offline repository. Preflight checks validate repository inputs, four baseline ZIPs, modules and JUnit coverage counts without weakening production enforcement. Runtime logs/data are excluded.

Use `-PacceptanceDirectory=jvm-acceptance-complete` to choose a new directory under build. Seal only before initial execution; never reseal executed inputs to conceal mutations. Linux runs as existing unprivileged nobody, without software installation or changes to existing services. Coverage is limited to these JVMs/architectures and fixtures, not arbitrary third-party self-managed resources.
