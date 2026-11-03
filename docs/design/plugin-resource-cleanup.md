# 插件停止与失败清理

[English](en/plugin-resource-cleanup.md)

## 第四轮局部修复设计

- 卸载遇到停止/排空失败立即向调用方报告，不移除插件记录、不关闭 loader；依赖方卸载失败同样中止，不允许带着仍在运行的依赖方卸载提供者。
- SpringExtensionFactory 回退创建的扩展保留原有构造和单例注册方式，补充 DisposableBean 登记，交由 PluginListableBeanFactory 的现有销毁观察及失败重试处理；不新增自动注入或初始化动作。
- Web RequestWork 的续接选择、计数加入、工作退出和完成释放统一使用已有 workTracker 监视器，避免向已释放对象追加计数；不增加新的状态机、配置或依赖。
- 升级兼容：公共 API、配置、插件描述符和存储格式不变。卸载失败现在显式失败而不是伪装成功；原先遗漏的扩展销毁回调会执行，属于资源所有权修正。不得依赖错误的强制卸载或漏销毁行为；回滚仍须先成功停止，不能通过回滚绕过资源关闭失败。
- 验证：覆盖阻塞工作/销毁失败后的卸载保留与恢复、依赖方失败、扩展关闭及失败重试、异步续接竞争和排空拒绝；两个实际 zip 插件验证跨插件 ASYNC 分派、阻塞时卸载拒绝、扩展真实端口释放及恢复。最终执行全量构建、双 profile smoke 和未重编译旧插件双 profile 升级验收。状态：本地验收通过，结果见第四轮验收记录。

## 第三轮局部修复设计

- 普通 forward/include 分派独立准入并持有自己的工作计数，内层完成只释放内层；异步续接复用仍在途的计数，并为新进入的插件补充准入。每个分派拦截器保存自己持有的工作对象，不通过共享请求属性猜测完成对象；同步内层完成后恢复外层属性。
- 异步回调以工作对象为 key，避免跨插件分派覆盖彼此的完成回调；保持响应完成后仍等待实际 Callable 退出的保护。
- 为直接注册的插件实例准备 Spring RootBeanDefinition，并让现有销毁处理器登记受管方法，再创建标准销毁适配器。沿用 Spring 的注解/DisposableBean 去重，不新增回调去重规则；禁用额外的推断 close，保持直接实例原有销毁选择。
- 验证分派计数、内外层完成、异步续接及重复销毁；真实 Tomcat 使用两个实际 zip 插件进行 forward/include、内层和外层阻塞验证，并检查插件销毁一次后可重启。状态：本地验收通过，结果见第三轮验收记录。

### 升级兼容性

- 不改公共方法签名、配置项默认值、管理 API JSON/错误码、插件描述符、信任清单和持久化格式，不要求已有插件重编译或迁移数据。
- 普通同步请求和已准入异步续接仍保持原计数语义；嵌套分派的在途值会正确反映多层工作，同插件嵌套可大于 1。监控应将其视为在途工作数量，而非去重后的 HTTP 请求总数。
- 同一插件销毁方法同时标注 PreDestroy 并实现 DisposableBean 时只执行一次；不兼容此前错误的重复调用依赖。直接实例不会因此新增推断 close，普通 Spring Bean 的销毁选择及失败重试语义保持原有规则。
- 验收使用修复前已构建的 81526a7 插件 zip 和 sidecar，保存 SHA-256 后，以修复后框架直接加载并执行业务、启停和 reload；新版夹具不代替旧二进制兼容性验收。

## 第二轮边界修复设计

复审确认 Callable 异步分派重复计数、宿主请求执行的插件拦截器未计数，以及 Spring 内部吞掉销毁异常三个缺口。本轮保持现有配置与 SPI：

- 每次请求只创建一份工作计数，覆盖 Controller 和实际使用的插件拦截器；异步再次分派复用计数，Servlet 异步完成/超时/错误均释放且仅释放一次。排空期间拒绝新增插件工作，已经进入的异步请求允许完成。
- Callable 响应终结后仍等待实际工作线程退出；尚未开始的工作在请求终结后拒绝执行。DeferredResult 的外部生产线程仍由插件自行管理。
- 注册的 HandlerMethod 保留插件 ID，准入时在计数器锁内核对当前所有权，拒绝已注销但此前已匹配的旧 handler；拦截器快照和所有权一起读取。
- 动态 MappedInterceptor 按其路径规则匹配；管理 API 继续使用已有 base-path，控制入口不执行插件动态拦截器，避免 stop 等待自己的控制请求。宿主侧拦截器保持原流程。
- 在现有 PluginListableBeanFactory 内包装 Spring 5.3 的销毁适配器，沿用其选定的注解、DisposableBean、AutoCloseable 和自定义回调。回调异常记录到已有 PluginCleanup；继续清理其它资源，关闭后统一报告。只保留失败回调供再次清理，成功回调不重复执行；字段不兼容时明确失败，不能静默放行。
- 直接注册的插件对象也纳入销毁回调，取消把字符串 beanName 误作实例传给 destroyBean 的调用。
- 销毁失败时保持 FAILED、保留待清理上下文并禁止直接创建新上下文；重新清理成功后才允许启动。无法强制修复插件自己持续抛错的关闭动作。
- Spring 适配仅使用当前 5.3 版本销毁元数据，定向测试覆盖四类标准回调、继承、重复方法排除及用户自定义处理器覆写。第三方处理器自行吞掉异常以及自定义 scope 的外部销毁，仍需其自身提供失败报告；不通过全局日志推断资源状态。

验证包含真实 MockMvc 异步重分派、阻塞全局拦截器、端口资源关闭失败及恢复，并在真实 zip 插件/Tomcat 宿主中验证。状态：已通过本地验收，结果见第二轮验收记录。

## 问题与范围

停止回调异常会截断清理；启动失败未撤销全局注册；普通 stop 未排空在途工作；调度扫描缓存长期持有插件 Class。
本次只调整 api/core/starter/web 的生命周期边界，JPA 资源仍由所属 Spring 上下文销毁。

## 方案

- 普通 stop 在修改任何插件前，对目标及依赖方统一 begin/await drain，复用 `plugin-drain-timeout-millis`；超时或中断保持原运行态并恢复入口。生命周期锁仍串行化操作；工作线程不得同步等待生命周期操作，否则排空会超时。
- 调度和 Web 共用原子准入/在途计数器；排空可嵌套，内层结束不能解除外层保护；任务完成后移除零计数。
- 停止回调、事件、资源 hook、共享注册、support 释放、全局上下文注销、context close 和 closed 回调逐项尝试，收集所有异常。只有全部成功才报告 STOPPED；失败保留 FAILED 和重试清理标志，start 必须先重试清理。
- 启动异常沿相同清理路径回滚。先排空可能已经启动的任务；排空失败时保留上下文以免关闭仍被使用的资源，后续 stop/start 必须重试。
- 全局上下文先注销再关闭；close 不再吞掉销毁异常。release hook 完成后移除，失败 hook 保留以便重试。
- 删除调度器跨生命周期的 Class 强引用缓存。stop 保留原始插件 loader 以支持 restart；unload/reload 关闭原始 loader。
- 插件上下文直接使用原始 loader。实测旧的临时 delegating loader 在 stop 时清空 CGLIB 缓存，导致复用原始 loader 的 JPA restart 重复定义代理类；取消该包装和 stop 阶段的反射式代理清理，保持生成代理的 loader 身份稳定。现有公共包装类型保留兼容，但框架不再创建它。
- 组合 support 的停止/释放回调相互隔离，单个 support 失败不能阻断其它 support。
- 清理宿主 MVC 中以插件类型和方法参数为键的 session/model/binder、参数解析和异常处理缓存。堆快照确认路由注销不足以解除 `RequestMappingHandlerAdapter.sessionAttributesHandlerCache` 的强引用；仅按插件 loader 定向移除，不增加新的运行时配置或治理层。

## 兼容性与边界

Java 8、模块边界、配置名及正常回调顺序不变。stop 现在可能因排空超时拒绝；清理失败显式报告并阻止直接重启。
插件自建线程、线程池、ThreadLocal、驱动及外部监听器必须由插件通过 Spring destroy 或 release hook 释放；框架不能强制安全终止任意线程。
不保证任意用户回调有界完成，也不把 loader.close 当作 GC 已回收的证明。
宿主依赖中的软引用缓存属于内存压力下可回收的缓存；smoke 使用 `-XX:SoftRefLRUPolicyMSPerMB=0`，使强引用残留验收可重复。该参数不加入生产配置。
构建在 JDK 9+ 使用 `--release 8`，core 显式依赖 JSR-250 注解，避免构建机器的 JDK API 影响 Java 8 兼容性。
本次保持现有 manager 的依赖排序与状态锁编排，仅抽出独立排空范围和异常收集，避免为修复清理问题同时重构全部生命周期职责。

## 验证

- 生命周期测试：正常及反复启停、停止/事件/support/hook 异常、启动失败后无全局注册、清理失败重试、排空超时不变更依赖范围。
- 调度/Web 测试：阻塞在途工作、拒绝新工作、嵌套排空、原子准入、释放计数和重复注册。
- 使用真实插件包的 runtime smoke 验证完整宿主、MVC/JPA、reload 和异常回归；完整 build 显式检查测试结果。
- 在最终代码上记录命令、结果及限制；不将 fake loader 单测视为真实类卸载验收。

## 第一轮验收记录（历史基线）

2026-11-03，Windows / Temurin JDK 17，编译使用 `--release 8`。状态：已通过本地验收，未发布。

```powershell
.\gradlew.bat build :samples:cross-plugin-jpa:app-run:runtimeSmoke :samples:cross-plugin-jpa:app-run:runtimeSmokeProduction --quiet
```

- 命令退出码 0；9 个框架模块的 JUnit XML 合计 303 项，失败、错误、跳过均为 0，确认测试实际执行。
- 生命周期测试完成 50 轮 Spring 上下文启停及 Bean 销毁；20 次连续启动失败未增加全局注册。覆盖 stop/hook/event 异常、失败步骤重试、初始化失败、阻塞任务排空超时保持依赖链不变、启动失败时保留在用上下文直到任务退出、部分共享注册回滚和宿主销毁阶段清理。
- 普通和 production smoke 各完成真实 zip 插件的 20 轮启停、10 轮 reload；10 个旧 loader 的 WeakReference 全部清空。启动/停止失败注入后，MVC/共享资源/任务/全局上下文计数归零，并可再次启动。
- 原有业务事务回滚、JPA reload/重启记录恢复、provider 替换、drain timeout 无变更、无关插件隔离和 Actuator 检查通过。
- 机器可读结果位于 sample app-run 的 `build/reports/runtime-smoke/result.json` 与 `build/reports/runtime-smoke-production/result.json`，并生成对应 JUnit XML。
- `git diff --check` 与变更文本的严格 UTF-8/替换字符检查通过。未在 Java 8 JVM 上运行本轮验收；插件自管且未注册销毁动作的外部资源不在自动释放保证内。

## 第二轮验收记录

2026-11-03，Windows / Temurin JDK 17 / `--release 8`。沿用上方完整构建和双 profile smoke 命令，最终退出码为 0。状态：本地验收通过，未做制品发布。

- 框架 JUnit XML 合计 313 项，失败、错误、跳过均为 0。新增销毁适配测试覆盖标准回调失败、继承和去重、自定义处理器覆写及仅重试失败动作；9 项 MVC/真实端口回归覆盖本轮边界。
- 普通和 production 的真实 Tomcat smoke 各完成 20 次 Callable/DeferredResult 请求；阻塞在宿主路由上的插件拦截器会阻断 stop；异步超时响应完成但 Callable 仍运行时，也拒绝 stop 并保持 STARTED。工作退出后 stop/start 成功。
- 真实插件持有的 ServerSocket 在注入关闭失败后仍占用原端口，框架报告 FAILED 且拒绝直接重启；关闭恢复后清理重试成功，新上下文可重新绑定原端口。
- 在上述异常恢复后，两套 smoke 各继续完成 20 轮启停、10 轮 reload 和 10 个旧 loader 回收；原有 JPA、业务事务、幂等、持久化、隔离及 Actuator 检查通过。
- 机器可读报告新增 `cleanupBoundaries=PASSED`；差异与 UTF-8 检查通过。未在 Java 8 JVM 上执行本轮测试，外部自管资源仍适用前述边界。

## 第三轮验收记录

2026-11-03，Windows / Temurin JDK 17 / `--release 8`。沿用上方完整构建和双 profile smoke 命令。状态：本地验收通过，未做制品发布。

- 框架 JUnit XML 共 321 项，失败、错误、跳过均为 0。新增 7 项回归覆盖直接插件销毁一次、跨插件 forward、内层完成保护外层、include 异常、同插件嵌套及两种异步续接；另有直接实例不新增推断 close 的升级回归。
- 普通和 production 的真实 Tomcat/zip 插件验收均通过 forward/include。内层阻塞时两个插件均不可停止；内层结束后外层仍不可停止，内层插件可独立重启；外层返回后插件销毁一次、原端口可重新绑定，插件可重启。报告包含 `dispatchBoundaries=PASSED`。
- 两套 smoke 的异步排空、销毁失败恢复、20 轮启停、10 轮 reload、10 个旧 loader 回收，以及 JPA、业务事务、持久化、隔离和 Actuator 原有检查继续通过。
- 修复前 `81526a7` 的四个实际插件 zip/sidecar 未重编译，直接加载修复后框架；普通和 production 各通过业务接口、20 轮启停、10 轮 reload、10 个旧 loader 回收和启动/停止失败恢复。脚本记录旧插件类签名以及插件/框架 SHA-256，报告为 `build/reports/legacy-plugin-compatibility{,-production}/result.json`（相对 sample app-run）。
- 差异与严格 UTF-8 检查通过。旧二进制验收只覆盖上述基线，不代表所有历史插件或第三方 Spring 扩展；未在 Java 8 JVM 上执行，外部自管资源仍适用前述边界。

复验需提供升级前留存的插件目录，不以重编译目录代替；以下基线位于仓库忽略的 `build` 中，不属于发布制品：

```powershell
$baseline = "$PWD\build\compatibility-81526a7\plugins"
& .\samples\cross-plugin-jpa\app-run\verify-legacy-plugin-compatibility.ps1 -BaselinePlugins $baseline
& .\samples\cross-plugin-jpa\app-run\verify-legacy-plugin-compatibility.ps1 -BaselinePlugins $baseline -Production
```

本轮旧 zip 的 SHA-256（文件版本均为 `3.3.0-SNAPSHOT`）：

| 插件 | SHA-256 |
| --- | --- |
| plugin-demo-jpa-domain | `6F8A886667E724323515B4DE26DA31B7491BCAB535CF9E757C1B76D1CED6FF04` |
| plugin-unrelated-service | `8482FC7A6189BDF3F0C18C5CE8F83CBB262D1B4A73B40D255E0BF07A8CA58F6E` |
| plugin-user-book-service | `C6707C64839B9C7B7AA458A6542B1FAFAA8BEF9AE55514528A31A17F6382A2C8` |
| plugin-workflow | `864BC0C8C112511EFAF586739006EEB9FA045C8C19B53E3C6726D878781CC2AB` |

## 第四轮验收记录

2026-11-03，Windows / Temurin JDK 17 / `--release 8`。状态：本地验收通过，未做制品发布。

- 执行上方完整构建和双 profile smoke 命令，并在保留全部原有测试后再次执行 `.\gradlew.bat build --quiet`，退出码均为 0。最终框架 JUnit XML 共 329 项，失败、错误、跳过均为 0。
- 新增 8 项回归：阻塞卸载保留并可恢复、清理失败卸载保留并可重试、依赖方失败阻止提供者卸载；扩展标准销毁/端口释放、失败动作单独重试、原有 Spring Bean 不重复销毁；两种异步完成与续接竞争（正常准入及排空拒绝）。保留直接实例原有初始化选择，不增加 PostConstruct 调用。
- 普通和 production 的真实 Tomcat/zip 插件验收均通过：拦截器阻塞时 unload 拒绝且原插件和上下文保留，释放后 unload/load/start 成功；非 Spring 扫描的 PF4J 扩展占有真实端口，销毁失败时 unload/start 不得绕过 FAILED，恢复后只成功关闭一次，端口可重新绑定并在新实例复用。报告新增 `unloadRetention` 与 `extensionDestruction`。
- 两套 smoke 各完成 20 次跨插件 Servlet ASYNC 分派，随后两个插件均可停止重启；前轮 forward/include、异步超时工作排空、销毁去重及关闭失败恢复继续通过。竞争窗口使用定向回归精确控制时序，不将普通 HTTP 重复请求冒充确定性竞争复现。
- 两套 smoke 在边界验收后继续完成 20 轮启停、10 轮 reload 和 10 个旧 loader 回收，JPA、事务回滚、记录持久化、provider 替换/隔离及 Actuator 检查通过。
- 使用第三轮记录的同一组 `81526a7` 旧 zip/sidecar 和相同脚本命令，在修复后框架的普通及 production 配置下均通过业务接口、20 轮启停、10 轮 reload、10 个旧 loader 回收及失败恢复。旧 zip 的 SHA-256 未变，报告记录的框架 JAR 与最终构建逐项校验一致。
- 差异及严格 UTF-8 检查通过。仍未在 Java 8 JVM 上运行；旧插件验收只覆盖指定基线，不能外推至所有历史插件。插件未登记的外部资源、任意不可终止的用户线程不在自动释放保证内。
