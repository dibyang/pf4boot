# Complex Cross-Plugin JPA Sample Implementation Plan

## 1. Goal and Scope

This plan tracks the migration from the current transitional demo to a standalone multi-module complex JPA sample.

Scope:

- Add the standalone complex sample module layout.
- Move entities out of the datasource capability plugin.
- Add cross-plugin service orchestration, transaction boundary demos, and failure scenarios.
- Update Chinese design docs, English translations, sample README, and acceptance records.

Out of scope:

- Cross-datasource atomic transactions.
- Plugin hot replacement implementation.
- Large-scale refactoring of `pf4boot-jpa-domain-starter`.

## 2. Milestones

| Milestone | Goal | Deliverable | Passing Condition |
| --- | --- | --- | --- |
| M1 Design freeze | Lock sample boundaries | Design doc and this plan | Responsibilities, dependencies, and verification commands are clear |
| M2 Sample skeleton | Create standalone multi-module sample | `samples/cross-plugin-jpa/*` modules | Empty skeleton compiles |
| M3 Model/provider split | Move entities out of datasource plugin | `model-*` and `plugin-demo-jpa-domain` | provider defines no entities/repositories/services |
| M4 Service/workflow implementation | Add complex business demo | service plugin, workflow plugin, controller | normal and failure paths are demoable |
| M5 Acceptance and docs | Produce reviewable evidence | sample README, acceptance doc, command logs | compile/package/HTTP smoke pass; remaining isolation acceptance is tracked separately |

## 3. Task Breakdown

### M1 Design freeze

- [x] Define responsibilities for `domain-model`, provider, service, and workflow.
- [x] State that entities must not live in datasource capability plugins.
- [x] State that cross-plugin access goes through exported services, not repositories.
- [x] State that `REQUIRES_NEW` must go through a separate bean.
- [x] Decide whether the sample is included in root `settings.gradle`.
- [x] Decide how model jars are provided at runtime.

### M2 Sample skeleton

- [x] Create `samples/cross-plugin-jpa`.
- [x] Create `demo-host`.
- [x] Create `model-user-book`.
- [x] Create `model-workflow-audit`.
- [x] Create `plugin-demo-jpa-domain`.
- [x] Create `plugin-user-book-service`.
- [x] Create `plugin-workflow`.
- [x] Create the `app-run` runtime packaging project.
- [x] Add sample README and minimal run instructions.

### M3 Model/provider split

- [x] Put `UserAccount`, `Book`, `WorkflowAudit`, and related entities into model modules.
- [x] Let the provider plugin depend on model modules and configure `entity-packages`.
- [x] Let the provider plugin only bundle model jars; framework/JPA starters are currently provided by the host classpath.
- [x] Ensure the provider plugin contains no repositories, controllers, or business services.
- [x] Verify provider packages do not contain business repositories.

### M4 Service/workflow implementation

- [x] Define repositories and `UserBookService` in `plugin-user-book-service`.
- [x] Explicitly bind repositories to `domain.demo.*` with `@EnableJpaRepositories`.
- [x] Define the audit repository in `plugin-workflow`.
- [x] Let `plugin-workflow` orchestrate user/book logic through `UserBookService`.
- [x] Use a separate writer bean to demonstrate `REQUIRES_NEW`.
- [x] Provide normal, failure, and query HTTP demo endpoints.

### M5 Acceptance and docs

- [x] Run sample compile verification.
- [x] Run sample plugin `pf4boot` package verification.
- [x] Start sample host and execute HTTP smoke.
- [x] Add the sample runtime packaging project, producing a runnable layout and zip distribution.
- [ ] Verify provider failure affects only the dependency chain; the current sample has no unrelated comparison plugin, so add a no-JPA/unrelated plugin or lifecycle test later.
- [x] Verify plugin package boundaries.
- [x] Update Chinese and English design docs.
- [x] Produce acceptance records.

## 4. Suggested Paths

If included in the root build, use these Gradle paths:

```text
:samples:cross-plugin-jpa:demo-host
:samples:cross-plugin-jpa:model-user-book
:samples:cross-plugin-jpa:model-workflow-audit
:samples:cross-plugin-jpa:plugin-demo-jpa-domain
:samples:cross-plugin-jpa:plugin-user-book-service
:samples:cross-plugin-jpa:plugin-workflow
:samples:cross-plugin-jpa:app-run
```

If kept independent, the sample should maintain its own `settings.gradle`, and the root project should reference it from documentation only.

## 5. Acceptance Checklist

| ID | Acceptance Item | Passing Standard |
| --- | --- | --- |
| AC-01 | provider has single responsibility | provider source contains no `@Entity`, repositories, controllers, or business services |
| AC-02 | entity ownership is clear | all entities come from `model-*` modules |
| AC-03 | shared transactions work | service/workflow plugins bind the same `domain.demo.transactionManager` |
| AC-04 | no cross-plugin repository injection | workflow does not inject repositories owned by service plugin |
| AC-05 | transaction proxy is effective | `REQUIRES_NEW` lives in a separate bean and is called externally |
| AC-06 | plugin package boundary is correct | provider contains or sees model classes, but not consumer repositories |
| AC-07 | failure scenario is demoable | forced workflow failure rolls back user/book through the main transaction while `REQUIRES_NEW` audit commits independently |
| AC-08 | dependency failure isolation works | provider failure affects only dependent plugins and not unrelated plugins; current sample has no unrelated comparison plugin yet |
| AC-09 | runtime package can be built | `app-run` assembles host dependencies, config, scripts, and plugin zips, and produces a distribution zip |

## 6. Risks and Mitigations

| Risk | Impact | Mitigation |
| --- | --- | --- |
| model jar is not visible at runtime | provider EMF creation fails | first version packages or explicitly exposes model jars to the provider |
| including sample in root build slows development | slower feedback | keep sample independent first, then decide optional root include |
| complex sample pollutes the root project | higher maintenance cost | remove the old root-level sample and keep business samples under `samples/cross-plugin-jpa` |
| `REQUIRES_NEW` demo is mistaken as default recommendation | wrong business usage | document it as a proxy-boundary demo, not the default pattern |
| multiple domains are mistaken as cross-database transactions | wrong consistency expectations | continue documenting that cross-domain atomic transactions are unsupported |

## 7. Open Question Recommendations

### Q1: Should the sample be included in root `settings.gradle`?

Decision: include it in the root `settings.gradle` so the sample reuses the current Gradle plugin and can be verified with normal project tasks. Sample modules are excluded from publishing.

### Q2: Who provides model jars?

Decision: in the first complex sample, the provider plugin carries the `model-*` jars. Framework and JPA starter libraries are currently provided by the host classpath to avoid Spring Boot, PF4J, and `pf4boot-api` type conflicts in plugin-private classloaders.

### Q3: What should happen to transitional complex code in the root demo?

Decision: after the complex sample lands, remove the old root-level sample project. Future samples should live under `samples/cross-plugin-jpa` or new `samples/*` modules.

### Q4: Should hot replacement be implemented together?

Recommendation: no. Hot replacement involves dependency ordering, draining, transaction quiescence, health checks, rollback, and classloader cleanup. It should have a separate design and acceptance plan.

## 8. Recommended Verification Commands

Phase one compile/package:

```powershell
.\gradlew.bat :samples:cross-plugin-jpa:plugin-demo-jpa-domain:compileJava `
  :samples:cross-plugin-jpa:plugin-user-book-service:compileJava `
  :samples:cross-plugin-jpa:plugin-workflow:compileJava
```

```powershell
.\gradlew.bat :samples:cross-plugin-jpa:plugin-demo-jpa-domain:pf4boot `
  :samples:cross-plugin-jpa:plugin-user-book-service:pf4boot `
  :samples:cross-plugin-jpa:plugin-workflow:pf4boot
```

Runtime packaging:

```powershell
.\gradlew.bat :samples:cross-plugin-jpa:app-run:sampleDistZip
```

Runtime smoke:

```text
GET /api/sample/workflow/place
GET /api/sample/workflow/place?failAfterAudit=true
GET /api/sample/workflow/summary
GET /api/sample/workflow/audit
```

## 9. Status

- Plan start date: 2026-06-11
- Current status: M5 acceptance phase; compile/package/package-boundary/HTTP smoke checks pass, while provider failure isolation needs an unrelated plugin or lifecycle test
- Owner: Codex
- Blocker: none; the remaining item is a follow-up isolation acceptance enhancement.
