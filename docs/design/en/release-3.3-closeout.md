# 3.3.0 Release Closeout

[中文](../release-3.3-closeout.md)

## Frozen Scope

Freeze features at code baseline `7ef78c0`. Only finalize the version, upgrade notes, published artifacts and upgrade acceptance. Add no APIs, runtime dependencies or lifecycle mechanisms. Record any acceptance-blocking fix and revalidate final artifacts.

## Three Gates

1. Set `3.3.0`; update CHANGELOG and release notes with defaults, intentional behavior corrections, migration and rollback limits.
2. Build on a real Java 8 JVM and run `publishToMavenLocal`. An independent consumer resolves all eleven framework modules by Maven coordinates, without project/includeBuild substitution. Check JARs, POMs, sources, Javadoc, internal versions and runtime classpath.
3. Reuse sealed acceptance on Windows and Linux. Additionally use the preserved original 3.2.0 distribution ZIP without recompiling old plugins. The cross-plugin JPA user/book/audit workflow is the representative in-project business case: transactions, repeated restarts, data-preserving upgrade and backup-based rollback. External business packages are excluded at the user's request; customer production plugins are not claimed as tested.

## Compatibility and Limits

- Production profile remains opt-in. Original 3.2.0 ZIPs lack new signed manifests: test compatibility in default mode and explicit rejection in strict mode. Do not modify old ZIPs, invent historical signatures or bypass gates.
- Rollback requires successful drain and shutdown. Keep the old host, plugins, configuration and offline data/record backup; restore the complete old snapshot. Do not assume old frameworks can read all new governance records or infer reverse compatibility from successful new-version writes.
- Run identical Java 8 bytecode on Windows and Linux. Linux uses an existing unprivileged user, isolated directory and loopback port, without changing services or Java.
- No automatic commit, push, tag, deployment or Central publication in this round. Central publication remains a manual operation requiring authorization.

## Acceptance Status

On 2026-11-04 all three gates passed for `3.3.0`, without publication. Framework runtime code is unchanged. The sample console's default SNAPSHOT filename was corrected and covered by a regression assertion.

- Real Java 8 full build, local Maven publication and independent POM consumption passed. All eleven built/published/consumed JAR hashes match. Sources, Javadoc, POMs and signatures are present; 55 attachment signatures passed offline verification; framework bytecode is at most version 52.
- Windows 8u431 and Linux 8u312 each passed 366 JUnit tests, three complete hosts and four existing legacy upgrades: sixteen matrix checks. Each also passed four original 3.2.0 checks: business initialization, independent 3.3.0 Maven-consumer upgrade (20 restarts, 10 reloads), old-backup rollback and strict-mode rejection.
- Old plugin ZIPs are unchanged. All eleven old framework JARs match preserved Maven Local 3.2.0 artifacts. External application.yml hashes match between versions; compatibility was not obtained by relaxing old configuration.
- Saga/Outbox smoke passed. Basic template hello, version/STARTED status and graceful shutdown passed. Console regression and final full build passed.
- All 1,167 sealed inputs remained unchanged on both platforms. Acceptance port 17832 and local template port 17833 were released. Linux only used a temporary directory and the existing nobody user, without changing services or Java.

See the [3.3.0 release notes](../../release-notes/3.3.0.md) for hashes, reports and commands. Export fixed two acceptance-script errors (Groovy interpolation and XML API usage); incomplete directories were retained as failed evidence, not treated as final candidates.
