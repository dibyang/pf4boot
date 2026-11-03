# 插件停止与失败清理

[English](en/plugin-resource-cleanup.md)

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

## 本地验收记录

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
