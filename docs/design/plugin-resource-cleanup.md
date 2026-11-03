# 插件停止与失败清理

[English](en/plugin-resource-cleanup.md)

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
