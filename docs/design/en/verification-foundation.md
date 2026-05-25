# Verification Foundation

## Problem

The framework covers high-risk paths such as plugin lifecycle, shared beans, dynamic MVC, JPA starter behavior, and plugin packaging, but test directories are mostly empty and the root build disables every task whose name contains `test`. A successful `build` currently proves compilation and packaging only; it does not prove plugin start, stop, reload, or dynamic cleanup behavior.

## Affected Modules

- `build.gradle`: adjust the test task policy so explicit test tasks can run.
- `pf4boot-core`: add lifecycle, shared bean, auto-export, and dynamic metadata boundary tests.
- `pf4boot-web-starter`: add dynamic controller and interceptor registration cleanup tests.
- `pf4boot-jpa-starter`: add JPA starter condition and package scan boundary tests.

## Proposed Design

Remove the root build logic that disables tasks by `test` in their name, keep the existing JUnit4 dependency, and add focused module tests. The tests should drive the current implementation directly instead of introducing a larger test framework or starting the full demo application.

`pf4boot-core` tests use lightweight Spring `ApplicationContext` instances and test plugin classes to cover:

- plugin start, stop, restart, and reload state plus hook order;
- stopping a dependency also stops its dependents;
- failed startup cleans up the plugin context;
- `ROOT`, `APPLICATION`, `PLATFORM`, and grouped shared bean registration and stop cleanup;
- duplicate exports do not remain after stop;
- `DynamicMetadata.sync()` fails explicitly.

`pf4boot-web-starter` tests instantiate `PluginRequestMappingHandlerMapping` directly and use test controllers, interceptors, and mock requests to verify dynamic mapping, interceptor registration, cleanup, and repeated registration behavior.

`pf4boot-jpa-starter` tests do not start a real database connection. The disabled condition test verifies the configuration class does not create JPA beans by default. The enabled boundary is covered by a test subclass exposing `getPackagesToScan()`, verifying that the plugin main class package can be used as the scan package. The dynamic sync failure remains covered by core tests.

## Compatibility

The test policy change makes explicit `test` tasks and tests inside `build` actually run. Runtime APIs, plugin packaging, and lifecycle ordering do not change. If a future build needs to skip tests, use Gradle's standard `-x test` instead of disabling tests globally from the root build.

## Verification

Minimum verification commands:

- `.\gradlew.bat :pf4boot-core:test`
- `.\gradlew.bat :pf4boot-web-starter:test`
- `.\gradlew.bat :pf4boot-jpa-starter:test`

Broad verification command:

- `.\gradlew.bat build`

## Open Questions

This phase establishes the regression test foundation only. It does not refactor Web/JPA starter dependency boundaries or implement runtime JPA metadata synchronization. Those should be designed separately.
