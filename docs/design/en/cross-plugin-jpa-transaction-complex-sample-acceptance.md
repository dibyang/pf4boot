# Complex Cross-Plugin JPA Sample Acceptance Record

## 1. Acceptance Time

- Date: 2026-06-11
- Scope: `samples/cross-plugin-jpa`

## 2. Conclusion

Compile, plugin packaging, runtime distribution packaging, package-boundary checks, and runtime HTTP smoke pass. The shared JPA bridge now exposes `domain.demo.entityManagerFactory` and `domain.demo.transactionManager` BeanDefinitions in consumer plugin BeanFactories, so service/workflow Spring Data repositories can start.

Failure-path acceptance semantics:

- `WorkflowServiceImpl.place()` wraps the cross-plugin main workflow with `domain.demo.transactionManager`.
- `UserBookServiceImpl.registerUserWithBook()` participates in the same domain transaction, so user/book writes roll back when the workflow forces an exception.
- `WorkflowAuditWriterImpl.append()` intentionally uses a separate bean plus `REQUIRES_NEW`, so the failure path keeps its audit row to demonstrate independent audit transactions and proxy boundaries.

## 3. Passed Items

| Item | Result | Evidence |
| --- | --- | --- |
| sample modules included in root build | pass | `settings.gradle` includes `samples:cross-plugin-jpa:*` |
| compile verification | pass | `.\gradlew.bat :samples:cross-plugin-jpa:demo-host:compileJava :samples:cross-plugin-jpa:demo-host:assembleSamplePlugins` |
| plugin packaging verification | pass | `assembleSamplePlugins` produces 3 plugin zips |
| runtime distribution package | pass | `:samples:cross-plugin-jpa:app-run:sampleDistZip` produces `build/runtime` and `pf4boot-cross-plugin-jpa-sample-*.zip` |
| provider single responsibility | pass | provider source contains no entities, repositories, controllers, or business services |
| entities in model modules | pass | `model-user-book`, `model-workflow-audit` |
| provider package boundary | pass | provider zip carries only its own jar and the two model jars |
| consumer package boundary | pass | service/workflow zips do not bundle Spring/JPA/PF4J/`pf4boot-api` |
| dependency metadata | pass | service depends on domain; workflow depends on domain and service |
| shared JPA bridge | pass | dynamic shared beans in `pf4boot-core` also register BeanDefinitions; the `pf4boot-jpa-starter` local placeholder BeanDefinitions are visible to Spring Data JPA |
| HTTP smoke | pass | `OK_STATUS=200`, success returns `{"books":1,"audits":1,"users":1}`; after the forced failure summary is `{"books":1,"audits":2,"users":1}` |
| transaction boundary demo | pass | failure path rolls back user/book while `REQUIRES_NEW` audit commits independently |

## 4. Additional Passed Items

| Item | Result | Current Behavior | Follow-up |
| --- | --- | --- | --- |
| provider failure isolation runtime acceptance | pass | P10 added `samples/cross-plugin-jpa:plugin-unrelated-service`; runtime smoke verifies the unrelated plugin still returns 200 after stopping `sample-demo-jpa-domain` | closed by P10-C in `plugin-framework-follow-up-hardening-acceptance.md` |

## 5. Runtime Findings

1. Plugins must not bundle `pf4boot-api` or PF4J, otherwise `@PluginStarter` can be hidden by plugin-private classloader types.
2. Plugins must not carry a private copy of Spring Boot/Spring auto-configuration classes, otherwise auto-configuration type checks can fail.
3. The current runnable boundary is: host classpath provides framework/JPA base libraries; the provider plugin carries model jars and activates the domain.
4. Spring Data JPA recursively scans parent BeanFactories for `EntityManagerFactory` beans and then looks up BeanDefinitions; platform-exported JPA beans must not be singleton-only and must also have BeanDefinitions.
5. The `REQUIRES_NEW` path is a proxy-boundary and audit-retention demo, not a default business recommendation.

## 6. Verification Commands and Results

### Compile and package

```powershell
.\gradlew.bat :pf4boot-core:compileJava :pf4boot-core:test :pf4boot-jpa-starter:compileJava :pf4boot-jpa-starter:test
```

Result: pass.

```powershell
.\gradlew.bat :samples:cross-plugin-jpa:demo-host:compileJava :samples:cross-plugin-jpa:demo-host:assembleSamplePlugins
```

Result: pass.

```powershell
.\gradlew.bat :samples:cross-plugin-jpa:app-run:sampleDistZip
```

Result: pass. The runtime layout contains `bin`, `config`, `lib`, and `plugins`, and the distribution zip is generated.

### HTTP smoke

```text
GET /api/sample/workflow/place?username=smoke-ok&password=123&bookName=BookA&failAfterAudit=false
=> 200 {"books":1,"audits":1,"users":1}

GET /api/sample/workflow/place?username=smoke-fail&password=123&bookName=BookB&failAfterAudit=true
=> 500

GET /api/sample/workflow/summary
=> {"books":1,"audits":2,"users":1}

GET /api/sample/workflow/audit?username=smoke-ok
=> 1 audit row

GET /api/sample/workflow/audit?username=smoke-fail
=> 1 audit row
```

Note: the failed `smoke-fail` user/book writes are not committed, while the audit row is retained by the independent `REQUIRES_NEW` transaction.
