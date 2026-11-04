# 插件停止与失败清理

[English](en/plugin-resource-cleanup.md)

## 释放钩子追加边界设计

- 释放钩子执行中追加新钩子时，单次快照结束可能仍有资源，却报告 stop 成功。保留快照；结束后列表未空则显式失败，由既有 pendingCleanup 在后续 stop/start 清理，不无限循环，不新增字段、公共 API 或依赖。
- 正常钩子顺序及成功回调仅执行一次的语义不变。行为修正仅针对清理中继续追加钩子；新资源释放前不允许再次激活。验证端口资源、持续自追加的有界失败、真实宿主及 Java 8/Linux 旧包升级。状态：本地及 Linux 验收通过。

### 追加钩子验收记录

2026-11-04，以 `4ef8284` 为基线。两项新增回归修复前均失败，修复后验证：停止明确失败、子钩子及真实端口仍由待清理记录持有；再次启动先完成子钩子，父/子各执行一次；持续自追加每次只执行一次快照、保持失败，修正回调后可恢复。

- 生产改动仅为原 releaseResource 快照结束后的空列表检查。正常成功钩子、失败钩子重试及 API/配置/插件格式均不变；不为此拆分已有生命周期临界区或新增通用机制。
- 真实 Windows Java 8u431 与 Linux OpenJDK 8u312 各通过 9 模块 / 366 项 JUnit、普通/production/Gson 三套完整宿主，以及 `41a04ab`、`81526a7` 两组未重编译旧包的四套升级。每端 16 个矩阵检查均通过。
- 三套宿主均新增 `appendedReleaseHook` 检查，验证新资源在失败停止后保留、激活前关闭、成功父钩子不重跑，并继续通过 31 轮 DTO 读写、20 轮启停、10 轮 reload/旧 loader 回收、JPA、分派、调度和关闭边界。旧包继续通过业务和异常恢复。
- 同一封存包压缩 SHA-256 为 `7d9943df6360697c4172e91dc4d0db31766d7a9051a388db6cb887fedc1f8033`，输入清单 SHA-256 为 `335abfba82b8327537dd0bf62d2e7b184f43fa719b21d945618ab387d660cd2a`。两端 848 个输入运行后未变，17821 端口已释放。
- 报告位于 `build/jvm-hook-review/reports/result.json` 和 `build/linux-hook-review-evidence/reports/result.json`，构建及封存方法复用 [Java 8/Linux 验收](java8-linux-acceptance.md)，导出参数为 `-PacceptanceDirectory=jvm-hook-review`。Linux 仍以已有 nobody 执行，未部署或修改系统服务。
- 这不保证任意未纳管线程在停止之后继续注册资源的安全；钩子失败仍需可重试且最终能完成。框架不会无限执行自追加回调或强杀线程。

真实 Java 8 JVM 与 Linux 的补充验收已完成，结果及验证边界见 [Java 8 与 Linux 运行验收](java8-linux-acceptance.md)。下述各轮的“尚未执行”是当轮历史状态。

## 第十二轮早期失败排空设计

- 复审 initiate 在创建上下文前失败且回滚排空超时的路径：清理计划尚未创建，FAILED 插件没有上下文，后续 start/批量 stop/close 会把它误认为无资源，可能重复初始化、留下端口，甚至卸载 loader 后资源仍活跃。
- 在启动失败进入排空之前，使用现有 pendingCleanup 的空值条目标记“欠清理但计划尚未执行”；不覆盖已有失败计划。所有现有准入及关闭判断已经使用 containsKey；排空恢复后，原 cleanupPlugin 将空值转为正常逐项清理计划，成功才移除。
- 仅 core 行为及样例夹具变化，不增加生产状态字段、表、公共 API、配置或依赖，不提前清理未排空资源。兼容性修正是拒绝绕过欠清理资源的再次启动/关闭；恢复后仍通过原 stop/start/unload 重试，旧插件无须重编译。
- 回归覆盖上下文创建前的端口资源、排空恢复后重启、卸载及宿主关闭保留/重试；真实 zip 中用在途工作线程验证。完整构建、三套 JSON 真实宿主及两组旧包四套升级验收以最终 JAR 为准。状态：本地验收通过，结果见第十二轮验收记录。

## 第十一轮 JSON 缓存清理设计

- 隔离复现：插件自有 DTO 经宿主 Jackson 序列化/反序列化后，原 MVC 清理仍留下 root deserializer 条目和旧类加载器；清除 mapper 缓存后 loader 才可回收。此前 Map 响应未覆盖插件 DTO 类型。
- 无 Jackson 时的 Gson 回退同样复现 typeTokenCache 清理前后残留 2 项；复用现有 Map 清理方法补齐该 converter，不增加 Gson 依赖，不更改实例或配置。增加 Gson 真实 HTTP 配置验收，避免仅验证默认 Jackson。
- 在 web-starter 现有 MVC 清理入口，按对象身份去重处理已配置的 Jackson/Gson converter 默认 mapper。不替换 mapper，不实例化新的宿主 Bean，不更改 module、mixin、subtype 或 JSON 设置；无 Jackson 的宿主不加载 Jackson 类型。插件工作已排空后清理，独立 converter 失败继续清理其它项并显式报告。
- 优先反射调用 Jackson 公开 clearCaches；旧 Jackson 2.13 路径沿用公开 serializer/type 缓存清理方法，仅对 root deserializer 和内部 deserializer cache 使用已验证字段适配，字段不兼容时明确失败。生产模块不增加 Jackson 依赖；测试依赖复用宿主 Boot JSON 版本。
- Jackson/Gson 没有按插件类型删除全部派生缓存的统一公共 API，因此只对 MVC 默认 mapper 的派生缓存整体失效，配置保持不变，下一次请求会重新预热；不新增缓存所有权注册表、开关或生命周期组件。显式注册的插件 module/mixin/subtype、按类型 mapper 配置及自管 mapper 不属于派生缓存，应由注册者撤销。
- 验证 converter 缓存归零、mapper 身份/设置不变、多 mapper 与失败重试，Jackson 2.13 和无 Jackson 路径；兼容性 init 脚本仅调整测试 runtime classpath，不改变发布依赖或宿主配置。真实 zip DTO 请求后反复 reload/GC，再执行完整构建、双 profile smoke 和两组旧包四套验收。公共 API、配置、插件包及旧插件二进制不变。状态：本地验收通过，结果见第十一轮验收记录。

## 第十轮刷新前失败清理设计

- 检查插件创建上下文后、refresh 前失败的路径：Spring 对未激活上下文的 close 不执行 destroySingletons，已手工登记的插件实例或资源销毁回调可能被遗漏，导致端口未释放及后续启动失败。
- 仅在现有 freeContext 的非激活分支补充 beanFactory.destroySingletons，先重试原销毁失败，再销毁尚未处理的单例，最后沿用既有关闭和失败校验。不重复执行成功销毁的回调，不新增清理注册表或状态。
- 范围为 api 的上下文释放、core 生命周期回归与样例验收夹具。正常刷新/停止顺序、公共签名、配置、依赖和插件格式不变；提前手工登记的销毁回调现在按所有权执行。失败仍显式报告，恢复后使用原有 stop/start 重试。
- 验证真实 ServerSocket 在刷新前失败后可重新绑定，连续失败后可恢复启动，销毁失败阻止不安全重启且仅重试失败动作；随后完整构建、双 profile zip/Tomcat 及两组旧包四套升级验收。状态：本地验收通过，结果见第十轮验收记录。

## 第九轮调度清理修复设计

- 复审共享 TaskScheduler 场景：注解处理先提交 fixedDelay/fixedRate 任务，后续冲突属性校验失败时句柄尚未登记，启动回滚、重试及卸载后共享队列仍残留任务。将现有每 Bean 的任务集合提前挂入任务组，提交成功即持有返回句柄；保持原注解校验顺序、错误和 scheduler 所有权。
- 复审已出队但尚未进入工作计数的旧任务：cancel(false) 不会中止已开始的调度包装；停止重启后只按 pluginId 准入会调用已销毁 Bean。执行器沿用现有任务组对象身份，在 workTracker 锁内同时校验该组仍为当前注册并准入计数，旧组不能借新生命周期重新执行。
- 任务组索引改为 ConcurrentHashMap，保留原有注册/注销同步；工作线程无须等待注册锁，避免引入新的排空互锁。不新增 generation 编号、状态表、配置或公共接口，也不重写 Spring 注解解析流程。
- core 回归覆盖两类冲突、反复失败及外部任务保留、停止重启和卸载后的旧任务拒绝；真实 zip/Tomcat 使用共享 scheduler 和有限时序屏障验收，再跑完整构建、双 profile smoke 及两组旧包四套升级验收。兼容性：正常定时参数、默认 scheduler、公共 API 及二进制不变；仅撤销错误启动留下的任务并阻止已停止实例再次执行。状态：本地验收通过，结果见第九轮验收记录。

## 第八轮宿主关闭修复设计

- 复审复现 manager.close 两个缺口：平台 ContextClosed 回调可重启已停止插件并留下活跃上下文/端口；排空超时被 bulk stop 记录后，close 仍关闭父上下文并返回成功。
- close 全程使用现有 stateLock；增加单个 shutdownStarted 准入标记，先停止自动启动调度，再停止插件。仍有运行实例、待重试清理或失败上下文时显式失败并保留 root/platform，后续 close 继续清理；root 已释放时重复 close 无操作。
- 关闭开始后拒绝新的 load/start/restart/reload/upgrade 和新平台上下文，允许已有上下文查询及 stop/unload 清理；已有上下文的查询不获取生命周期锁，避免排空中的任务被只读查询阻塞。首次创建平台上下文属于激活操作，应在任务运行前准备，不在需要排空的工作中同步等待该操作。调度任务取得锁后重新核对关闭标记。激活回调中的递归 close 被拒绝，复用既有 starting 标记并正确恢复嵌套状态，避免把正在启动的实例漏出停止快照。
- 公共签名、配置、插件格式不变。兼容性修正：关闭失败不再伪装成功；同一个 manager 开始关闭后只允许完成清理，不支持重新激活，应创建新 manager。宿主自身若继续销毁其它外部依赖，框架不能替宿主强行保活或终止任意用户线程。
- core 回归覆盖关闭回调、排空/清理失败重试、幂等、并发准入、启动回调和已出队自动任务；双 profile 真实宿主在全部业务验收末尾单独执行 shutdown 边界，旧插件升级验收继续保留。状态：本地验收通过，结果见第八轮验收记录。

## 第七轮清理重入修复设计

- 复审复现 PreStop 回调递归 stop/start：pendingCleanup 中正在执行的对象被当作可 retry 的失败计划，嵌套 stop 提前宣称完成，重启创建的新上下文最终关闭却未从全局注册表移除。
- 在现有 PluginCleanup 内记录 run 的活动深度；retry 在任意清理动作尚未退出时立即拒绝，不清空失败记录、不执行待重试动作。嵌套 run 仍允许；动作退出后，原有失败步骤重试行为保持不变。不新增生命周期状态表或公共方法。
- API/core 定向回归覆盖活动清理保护、嵌套动作、正常停止回调重入、启动失败清理重入及后续恢复；真实 zip/Tomcat 验证停止中的 restart 被拒绝、上下文/全局注册归零、端口释放及重新启动。
- 兼容性：公共签名、配置、二进制及存储格式不变；只拒绝不安全的递归清理重试。调用方应在停止完成后再发起 restart，而非在其清理回调内同步等待；未处理该拒绝的回调仍按现有机制报告失败，修正回调后可以重试。状态：本地验收通过，结果见第七轮验收记录。

## 第六轮局部修复设计

- 整批资源卸载与依赖图校正结束后，再按原依赖顺序发布收集的 UNLOADED 事件；复用本地事件列表和 PluginCleanup，不在批次中途调用监听器。尚未处理的提供者不会被同线程通知重新启动后又被关闭。
- loader 的 IOException、RuntimeException 和 Error 均进入现有 FAILED/移出 resolved/保留 wrapper 与 loader 的失败保护，禁止在部分关闭的 loader 上启动；恢复后仍通过 unload 重试并重新加载，不添加新的状态表。
- 扩展登记从名称集合改为名称到实例的内部记录，注销时复用共享 Bean 的身份核对；被 REPLACE 替换的实例不属于旧插件，只清除旧登记，原扩展仍由原插件上下文销毁。
- 兼容性：公共 API、插件二进制格式和配置默认值不变；UNLOADED 通知延到整批卸载收尾后，回调不能假设批次中的其它插件尚存。关闭异常现在统一拒绝不安全重启，REPLACE 不再被旧插件停止误撤销。没有新增运行时开关或依赖。
- 回归覆盖通知重入、真实端口、非受检关闭异常/错误和重试、扩展替换及登记失败回滚；双 profile 真实 zip smoke 使用现有自定义 loader 配置注入部分关闭失败，旧 zip 升级验收仍使用默认 loader。状态：本地验收通过，结果见第六轮验收记录。

## 第五轮卸载边界修复设计

- 完整 unload 使用现有 stateLock，与 start/stop/reload 串行化；先收集依赖方优先的去重顺序，对整个范围统一排空，再停止全部受影响插件，最后按叶子到提供者关闭 loader 和移除记录。排空失败不修改任何插件。
- 使用现有 PluginCleanup 收集卸载通知异常，不让监听器中断其它插件的卸载和依赖图刷新。通知发生在 loader 关闭和记录移除后；调用方仍收到通知失败，但不重复销毁已卸载实例。
- loader 关闭失败时保留 wrapper/loader，标记 FAILED 并从 resolved 列表移出，防止在可能已部分关闭的 loader 上重新启动；只允许重试 unload 后重新加载。不中断后续依赖图校正，不新增清理状态表或公共 API。
- 兼容性：配置、插件格式及公共方法签名不变；修正卸载顺序，并发启动不会穿过卸载临界区；UNLOADED 通知现在意味着 loader 已关闭。监听器失败仍显式报告，已完成的资源清理不回滚；旧代码不得依赖通知时 loader 仍可用。关闭失败不再允许不安全的直接重启。
- 回归覆盖三级依赖、菱形依赖、阻塞叶子无变更、并发启动、单个监听器和多插件通知失败、loader 关闭失败重试。真实 zip 插件覆盖多级依赖及并发/监听器边界；普通及 production 完整 smoke 和旧插件二进制升级验收均需通过。状态：本地验收通过，结果见第五轮验收记录。
- 升级验收追加定位：`3a43ec2` 实际旧插件稳定留下一个旧 loader。隔离堆的强引用链为存活上下文 → parentBeanFactory → mergedBeanDefinitions → RootBeanDefinition.resolvedTargetType → 旧 CleanupProbeExtension。Spring 5.3 注销只将合并定义标记 stale，未必移除旧引用；在动态 Bean 注销后仅移除该名称的合并定义缓存，保留其它项。使用当前 Spring 字段适配，字段不兼容时显式失败，不静默跳过；不扩大为全局缓存清空。

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

## 第五轮验收记录

2026-11-03，Windows / Temurin JDK 17 / `--release 8`。状态：本地验收通过，未做制品发布。

- 最终执行完整 `build`、`runtimeSmoke`、`runtimeSmokeProduction`，命令同前，退出码 0。框架 JUnit XML 共 335 项，失败、错误、跳过均为 0；新增 6 项回归覆盖三级链排空无变更与恢复、菱形顺序去重、并发启动互斥、多插件通知失败不中断关闭及依赖图刷新、loader 关闭失败阻止重启并可重试，以及只移除指定动态 Bean 的 stale 合并定义。
- 普通和 production 的真实 Tomcat 验收均通过 `unloadBoundaries`：workflow 请求阻塞时，domain/service/workflow 原实例和 loader 全部保留；释放后按 workflow → service → domain 卸载，再加载启动整条链，业务调用与事务回滚正常。该样例同时包含 workflow 对 domain 的直接依赖，覆盖共享依赖去重。
- 两个并发调用方验证 start 等待完整 unload 临界区；卸载完成后旧实例不可重启，重新加载可启动。UNLOADED 监听器抛错后仍完成 loader 关闭、上下文/全局注册清理，重复卸载不重复销毁，并可重新加载启动。
- 在新场景后，双配置继续通过原有异步/转发/扩展端口边界、20 轮启停、10 轮 reload 和 10 个旧 loader 回收，以及 JPA reload、持久化、provider 替换/隔离及 Actuator 回归。
- 保留 `81526a7` 和修复前 `3a43ec2` 两组实际 zip/sidecar，均未重编译；两组各在普通及 production 下通过业务调用、20 轮启停、10 轮 reload、10 个旧 loader 回收和启动/停止失败恢复。报告为 `legacy-plugin-compatibility{,-production}/result.json` 及 `legacy-plugin-compatibility{,-production}-3a43ec2/result.json`，均相对 sample app-run 的 `build/reports`。
- `3a43ec2` 最初与单独复验均出现 1 个旧 loader 残留；隔离堆定位到上述平台合并定义强引用。定向清理修复后，保持原 GC 次数和标准重新运行全部验收通过，不将预热后未观测到初始 loader 的 smoke 结果替代冷启动升级验收。临时堆诊断、探针和堆文件只在忽略的 build 目录，未加入提交。
- 全部旧 zip SHA-256 未变，升级报告的框架 JAR 与最终构建逐项哈希一致；差异和严格 UTF-8 检查通过。未在 Java 8 JVM、其它操作系统或所有历史插件上验证，自管资源仍适用前述边界。

新增旧包基线的复验命令（前述 `81526a7` 命令仍适用）：

```powershell
$baseline = "$PWD\build\compatibility-3a43ec2\plugins"
& .\samples\cross-plugin-jpa\app-run\verify-legacy-plugin-compatibility.ps1 -BaselinePlugins $baseline -BaselineRevision 3a43ec2
& .\samples\cross-plugin-jpa\app-run\verify-legacy-plugin-compatibility.ps1 -BaselinePlugins $baseline -BaselineRevision 3a43ec2 -Production
```

`3a43ec2` 旧 zip 的 SHA-256（版本 `3.3.0-SNAPSHOT`）：

| 插件 | SHA-256 |
| --- | --- |
| plugin-demo-jpa-domain | `90B16514393F3A2BF975645C53999D808C15CBF2A82E047062C73F4167488B6A` |
| plugin-unrelated-service | `0C7CF4D3CF5B9A9C7F3EDD4FBA790DC156EBD061FDB946C7D58A4475730C3340` |
| plugin-user-book-service | `E672B5ED88A99836687D90CCF4D571259E7ACBDD15FFE49038926685D30822B2` |
| plugin-workflow | `E3B2F9393A469970F7C6F316FB3ACDA619BA74989DD30341AC52DF9D25495E77` |

## 第六轮验收记录

2026-11-03，Windows / Temurin JDK 17 / `--release 8`。状态：本地验收通过，变更留在工作区，未提交推送或发布制品。

- 最终完整构建和双 profile smoke 命令同前，退出码 0；框架 JUnit XML 共 340 项，失败、错误、跳过均为 0。新增 5 项回归覆盖整批通知重入与端口释放、RuntimeException 和 Error 两类部分关闭失败、扩展替换保留、扩展登记失败回滚及恢复。
- 普通和 production 的真实 zip/Tomcat smoke 通过现有 `custom-plugin-loader` 配置使用 `SampleUnloadFailureLoader`，复用正式 zip 展开与加载，仅在 smoke 属性开启时于真实 loader 关闭后抛异常；未修改生产配置默认值。异常后保留 FAILED、拒绝在旧 loader 上创建上下文，清理重试和重新加载成功。默认 loader 路径由下面四套旧插件验收覆盖。
- 每个批次通知到达前，domain/service/workflow 的记录和上下文均已清理、loader 均已关闭；通知中重入 start(domain) 得到 PluginNotFoundException，不能留下新上下文。多级依赖卸载顺序、并发启动等待和监听器异常后续清理继续通过。
- REPLACE 后停止旧扩展所属插件，宿主替换实例仍在；原扩展端口可以重新绑定。由验收入口撤销其自建替换后，原插件可重新启动，未将宿主替换的清理责任转给旧插件。
- 两套 smoke 在新边界后继续通过 20 轮启停、10 轮 reload、10 个旧 loader 回收，以及异步、转发、事务回滚、JPA reload/持久化、provider 替换/隔离及 Actuator 检查。`unloadBoundaries` 新增 `reentrantStartRejected`、`uncheckedLoaderClose` 和 `extensionReplacement`。
- 保留的 `41a04ab` 与 `81526a7` 实际旧 zip/sidecar 均未重编译，两组各在普通和 production 下以默认 loader 通过业务、20 轮启停、10 轮 reload、10 个旧 loader 回收与失败恢复。报告为 sample app-run `build/reports/legacy-plugin-compatibility{,-production}-41a04ab/result.json` 及原 `81526a7` 报告路径。旧 zip 哈希未变，全部报告中的框架 JAR 与最终构建哈希一致。
- 差异及严格 UTF-8 检查通过。未在 Java 8 JVM 或其它操作系统上运行；不把选定旧包的兼容性验证外推到所有历史版本或任意插件自管资源。

`41a04ab` 基线复验命令：

```powershell
$baseline = "$PWD\build\compatibility-41a04ab\plugins"
& .\samples\cross-plugin-jpa\app-run\verify-legacy-plugin-compatibility.ps1 -BaselinePlugins $baseline -BaselineRevision 41a04ab
& .\samples\cross-plugin-jpa\app-run\verify-legacy-plugin-compatibility.ps1 -BaselinePlugins $baseline -BaselineRevision 41a04ab -Production
```

该组旧 zip 的 SHA-256（版本 `3.3.0-SNAPSHOT`）：

| 插件 | SHA-256 |
| --- | --- |
| plugin-demo-jpa-domain | `05346C71758F884790049378EDF61A73FAC876AC9E68B25BBB53FB66E9C2AD7B` |
| plugin-unrelated-service | `E318D0F8C2C61989BDF6CE770E180CF5667B97CAFD3C30147FF892FFDCDF9C87` |
| plugin-user-book-service | `B468463003AF03CED87580232202A783D38214CABA6774C6EE22EAF83CA9153C` |
| plugin-workflow | `40E3D58625739BB1AD2BBD36E1F3A2A313EB6F48A72EDF77E90FBB846D2CCB88` |

## 第七轮验收记录

2026-11-03，Windows / Temurin JDK 17 / `--release 8`。状态：本地验收通过，沿用工作区改动，未提交推送或发布。

- 最终执行前述完整构建及双 profile smoke，退出码 0。框架 JUnit XML 共 344 项，失败、错误、跳过均为 0；新增 4 项回归覆盖活动 retry 保留失败列表、嵌套 run 的外层保护、正常停止回调重入，以及启动失败清理重入/回调修正后的恢复。
- 修复前独立复现为 stop 返回 STOPPED、当前上下文为空，但新建后已关闭的上下文仍在全局注册表。修复后同一未处理拒绝的回调使 stop 显式失败，未创建替换上下文、无全局注册残留；已捕获拒绝的正常停止回归则为 STOPPED，资源仅销毁一次。移除问题回调后原有清理重试及启动成功。
- 普通和 production 的真实 zip/Tomcat 验收均新增 `cleanupReentry=PASSED`：PreStop 内的 restart 被拒绝，外层停止完成后上下文和全局注册归零，原扩展端口可重新绑定，随后启动并执行业务成功。
- 双配置继续通过第六轮全部边界、20 轮启停、10 轮 reload、10 个旧 loader 回收，以及异步/转发、事务、JPA reload/持久化、provider 替换/隔离及 Actuator 检查。
- 在最终代码上重跑第六轮记录的 `41a04ab` 和 `81526a7` 两组未重编译旧 zip/sidecar，普通及 production 四套默认 loader 验收均通过业务、20 轮启停、10 轮 reload、10 个旧 loader 回收和启动/停止失败恢复。沿用上述脚本、报告路径和旧 zip 哈希；报告中的全部框架 JAR 哈希与最终构建一致。
- 差异和严格 UTF-8 检查通过。本轮只修复新增实证的一处清理重入遗漏，没有新增公共方法、配置或生命周期组件；Java 8 JVM、其它操作系统以及任意自管资源仍未由本次验收覆盖，不宣称穷尽所有插件行为。

## 第八轮验收记录

2026-11-03，Windows / Temurin JDK 17 / `--release 8`。状态：本地验收通过，工作区改动未提交推送或发布。

- 最终完整构建及双 profile smoke（命令同前）退出码 0。框架 JUnit XML 共 351 项，失败、错误、跳过均为 0。新增 7 项回归覆盖关闭回调与幂等、任务排空失败保留父上下文后重试、失败清理重试不重复销毁、并发 start 关闭后拒绝、激活回调 close 拒绝及嵌套标记恢复、已出队自动启动任务关闭后不再执行，以及排空中的任务仍可查询已有平台上下文。
- 收尾的确定性测试先复现已有上下文查询等待生命周期锁、反向阻塞 close 排空；恢复已有项无锁查询后转绿，新建上下文仍受锁及关闭准入控制。此后重新执行完整构建、双配置 smoke 和四套旧包验收，以最终 JAR 为准。
- 修复前独立复现：ContextClosed 内启动返回 STARTED，manager.close 返回后插件上下文和全局注册仍在、ServerSocket 未关闭；阻塞工作时 close 返回成功，插件活跃但父上下文已关闭。修复后分别为停止态/无上下文或端口残留，以及显式失败/插件和父上下文仍活跃，等待任务退出后可再次 close。
- 普通及 production 的真实 zip/Tomcat 在全部业务、JPA、Actuator 和反复启停验收后，执行 `managerCloseBoundaries=PASSED`：阻塞拦截器时 close 拒绝并保留 root/platform，关闭期间拒绝 load/start；释放任务后重试 close 成功，平台关闭回调中的 start 被拒绝，重复 close 无副作用，全局插件上下文归零、扩展端口可重新绑定。
- 双配置此前全部边界、20 轮启停、10 轮 reload 和 10 个旧 loader 回收继续通过。关闭是终态，因此本轮 shutdown 场景之后只验证准入拒绝与资源释放，不再把同一个 manager 重新启动当作受支持流程。
- 在最终代码上重跑 `41a04ab`、`81526a7` 两组未重编译旧 zip/sidecar，普通/production 四套默认 loader 验收全部通过业务、20 轮启停、10 轮 reload、10 个旧 loader 回收及失败恢复；沿用原脚本、报告路径和旧 zip 哈希。报告的框架 JAR 与最终构建哈希一致，差异及严格 UTF-8 检查通过。
- 变更只使用现有锁、已有激活标记及一个关闭准入标记，不增加公共方法、配置或依赖。未在 Java 8 JVM、其它系统及外部现场执行；宿主继续销毁其自管依赖、任意未登记资源或不退出线程不在框架强制保护范围内。

## 第九轮验收记录

2026-11-03，Windows / Temurin JDK 17 / `--release 8`。状态：本地验收通过，保留前轮工作区改动，未提交推送或发布。

- 最终执行 `./gradlew.bat build :samples:cross-plugin-jpa:app-run:runtimeSmoke :samples:cross-plugin-jpa:app-run:runtimeSmokeProduction --quiet`，退出码 0。框架 JUnit XML 共 355 项，失败、错误、跳过均为 0。
- 新增 4 项回归在修复前全部失败、修复后全部通过：fixedDelay/fixedRate 与 fixedRate/fixedRateString 两类冲突分别连续失败启动 5 次，共享 scheduler 中只保留宿主对照任务；已出队旧任务在停止重启或卸载后均不再调用已销毁 Bean，新生命周期任务仍可执行。
- 独立探针修复前共享队列在首次失败、再次失败、卸载后分别残留 1、2、2 个任务，修复后三处均为 0。旧任务探针修复前在 Bean 已关闭后仍调用 1 次，修复后为 0，未发生关闭后执行。
- 普通及 production 真实 zip/Tomcat 均新增 `schedulingBoundaries` 验收：`invalidStarts=5`、`schedulingRollback=PASSED`、`staleTaskAdmission=PASSED`、`hostWorkPreserved=PASSED`。共享 scheduler 不被插件停止关闭，取消不波及宿主任务；测试结束后单独清理测试 scheduler。
- 双配置继续通过既有清理、转发、卸载和关闭边界、20 轮启停、10 轮 reload、10 个旧 loader 回收，以及事务、JPA 持久化/provider 替换和 Actuator 检查。
- 在最终 JAR 上重跑未重编译的 `41a04ab`、`81526a7` 旧 zip/sidecar，普通/production 四套默认 loader 验收全部通过业务、20 轮启停、10 轮 reload、10 个旧 loader 回收和失败恢复。报告路径及旧包哈希沿用前述记录，四套报告中的框架 JAR 和旧插件 ZIP 均与磁盘 SHA-256 一致。
- 差异及严格 UTF-8 检查通过。仅提前登记现有任务句柄并校验任务组对象身份，不新增公共 API、配置、依赖或 generation 注册表。未在 Java 8 JVM、其它系统或外部现场验收，任意自定义 scheduler 的异常实现、自管线程和未登记外部资源仍不能据此宣称无泄漏。

## 第十轮验收记录

2026-11-03，Windows / Temurin JDK 17 / `--release 8`。状态：本地验收通过，前轮改动保留，未提交推送或发布。

- 两项新增回归在修复前均失败：刷新前失败未调用已登记销毁动作，关闭返回后端口仍被占用，且销毁失败未进入待重试清理。修复后全部通过；连续失败 5 次均销毁并释放真实端口，随后可正常启动和停止。
- 销毁失败回归确认：保留原未激活上下文及资源、拒绝创建新实例，恢复后只重试失败动作，已经成功销毁的对照单例不重复销毁；后续启动/停止仍正常。现有生命周期测试一起通过。复用该测试类的宿主、wrapper 和生命周期夹具，未为这一个局部缺口拆分既有测试结构。
- 最终完整构建及双 profile smoke 命令同第九轮，退出码 0。框架 JUnit XML 共 357 项，失败、错误、跳过均为 0。
- 普通及 production 真实 zip/Tomcat 新增 `preRefreshBoundaries`：`failedStarts=5`、`earlyResourceRelease=PASSED`、`destructionRetry=PASSED`。每次失败后检查端口重新绑定、上下文/全局注册/映射/任务/共享服务归零；销毁失败时检查端口仍被占用及原上下文保留，恢复后释放并正常启动。
- 双配置继续通过原有清理、分派、调度、卸载和宿主关闭边界，20 轮启停、10 轮 reload、10 个旧 loader 回收，及 JPA/事务/Actuator 验收。新夹具使用单独 smoke 入口，不改变旧插件使用的原 lifecycle-probe 流程。
- 最终框架直接加载未重编译的 `41a04ab`、`81526a7` zip/sidecar，普通和 production 四套验收全部通过业务、20 轮启停、10 轮 reload、10 个旧 loader 回收及失败恢复。报告路径和旧包哈希沿用前述记录；四套报告的框架 JAR、旧插件 ZIP 均与磁盘 SHA-256 一致。
- 差异及严格 UTF-8 检查通过。本轮生产变更仅补齐非激活上下文的单例销毁，沿用原失败收集/重试，不增加公共接口、配置、依赖或清理状态。未在真实 Java 8 JVM、其它系统或外部现场执行；任意自管线程、未登记资源仍需插件自身负责，不能据此承诺所有插件绝无泄漏。

## 第十一轮验收记录

2026-11-04，Windows / Temurin JDK 17.0.20 / `--release 8`。状态：本地验收通过，前轮工作区改动保留，未提交推送或发布。

- 修复前隔离探针：Jackson root deserializer 清理后仍为 1 项、旧 loader 仍被持有；修复后为 0 项且 loader 可回收。Gson typeTokenCache 原为 2 项、清理后仍为 2 项；修复后为 0 项。两条路径均使用插件 loader 加载的独立 DTO，不以 Map 代替。
- 4 项新增回归均先复现失败再转绿，覆盖 Jackson 双向缓存、mapper 身份/命名设置保留、多 converter 共享与独立 mapper、非 public 自定义 converter 的失败报告/继续清理及重试，以及实际 Gson converter 的读写、缓存归零和配置保留。
- 当前宿主 Jackson 为 2.21.2、Gson 为 2.12.1。兼容性 init 脚本在 Jackson 2.13.5 下通过 4 项 JSON 回归；完全移除 Jackson 后通过 Gson 与原 MVC 共 12 项检查。脚本只调整测试 runtime classpath；生产 runtimeClasspath 确认没有引入 Jackson 依赖。PowerShell 的版本参数需加引号，命令如下：

```powershell
$init = 'pf4boot-web-starter/src/test/gradle/jackson-compatibility.init.gradle'
.\gradlew.bat :pf4boot-web-starter:test --tests '*CacheCleanerTest' -I $init '-PjacksonCompatibility=2.13.5' --offline
.\gradlew.bat :pf4boot-web-starter:test --tests '*PluginGsonCacheCleanerTest' --tests '*PluginRequestMappingHandlerMappingTest' -I $init '-PjacksonCompatibility=none' --offline
```

- 兼容性变体结束后，使用标准依赖重新执行 `./gradlew.bat build :samples:cross-plugin-jpa:app-run:runtimeSmoke :samples:cross-plugin-jpa:app-run:runtimeSmokeProduction --quiet`，退出码 0。最终框架 JUnit XML 共 361 项，失败、错误、跳过均为 0。
- 普通及 production 的真实 zip/Tomcat 均通过 31 轮插件 DTO GET/POST 读写、20 轮启停、10 轮 reload、10 个旧 loader 回收及失败恢复；输出确认 `jsonConverter=MappingJackson2HttpMessageConverter`。原有清理、转发、调度、卸载、刷新前失败、JPA/事务、Actuator 和最终宿主关闭边界全部继续通过。
- 另在 7794 端口使用测试专用 `src/smoke/resources/application-gson.yml` 完整复跑相同流程，断言实际使用 `GsonHttpMessageConverter`；31 轮 DTO 读写、20 轮启停、10 轮 reload/旧 loader 回收及其它完整 smoke 检查全部通过。报告为 `samples/cross-plugin-jpa/app-run/build/reports/runtime-smoke-gson/result.json`。复跑方式：

```powershell
$r = (Join-Path $PWD 'samples/cross-plugin-jpa/app-run').Replace('\', '/')
java '-Dsample.expected-json-converter=GsonHttpMessageConverter' -cp "$r/build/classes/java/smoke" net.xdob.sample.smoke.RuntimeSmokeRunner --runtime "$r/build/runtime" --port 7794 --config-location "config/application.yml,file:$r/src/smoke/resources/application-gson.yml" --result "$r/build/reports/runtime-smoke-gson/result.json" --junit "$r/build/test-results/runtimeSmokeGson/TEST-runtime-smoke-gson.xml"
```

- 最终 JAR 再次直接加载未重编译的 `41a04ab` 和 `81526a7` zip/sidecar，普通/production 四套验收均通过业务、20 轮启停、10 轮 reload、10 个旧 loader 回收及失败恢复；原 lifecycle-probe 不依赖新 DTO，旧包无须重编译。沿用前述旧包及报告路径，四套报告的框架 JAR、旧 ZIP 均与最终磁盘 SHA-256 一致。
- 差异及严格 UTF-8 检查通过，隔离 javac 生成的空根目录 extensions.idx 已移除。生产变更限于现有 Web 缓存清理类，不新增运行时依赖、公共 API、宿主配置项或状态组件；JSON 缓存清理后的预热开销是明确取舍。
- 未在真实 Java 8 JVM、其它系统或外部现场执行。显式类型注册、module/mixin/subtype、长持有 ObjectReader/ObjectWriter、自管 mapper/线程及未登记资源仍需所属代码撤销；本次不承诺任意插件与自定义 JSON 库均绝无泄漏。

## 第十二轮验收记录

2026-11-04，Windows / Temurin JDK 17 / `--release 8`。状态：本地验收通过，保留前轮工作区改动，未提交推送或发布。

- 3 项新增回归修复前全部失败、修复后全部通过。复现同一根因的三种后果：创建上下文前失败并排空超时后仍重复调用 initiate；任务退出后卸载了 loader 却未调用 stop 释放端口；宿主 close 跳过该失败插件并关闭父上下文。恢复后分别验证先清理再启动、卸载前资源释放，以及失败关闭保留父上下文并可重试/幂等关闭。
- 生产逻辑仅在启动失败进入排空前，向现有 HashMap 待清理表补入空值占位，不覆盖实际失败计划；空值表示尚未执行清理，containsKey 表示仍欠清理。原 cleanupPlugin 在排空成功后建立正常计划，成功后移除。没有新字段、注册表、配置、依赖或公共 API。
- 最终执行 `./gradlew.bat build :samples:cross-plugin-jpa:app-run:runtimeSmoke :samples:cross-plugin-jpa:app-run:runtimeSmokeProduction --quiet`，退出码 0。最终框架 JUnit XML 共 364 项，失败、错误、跳过均为 0。
- 普通、production 和沿用第十一轮命令的独立 Gson 真实 zip/Tomcat 全部通过 `earlyStartupDrain`：`inFlightDrain=PASSED`、`activationRetry=PASSED`、`socketRecovery=PASSED`。夹具在 initiate 内打开真实端口并启动在途线程，然后在创建上下文前失败；线程未退出时拒绝重复初始化和卸载，退出后工作计数归零，stop 恰好释放一次、端口可重新绑定，随后正常启动成功。测试线程、root 回调、属性和超时设置均在 finally 清理/恢复。
- 三套宿主继续通过 31 轮插件 DTO GET/POST 读写、20 轮启停、10 轮 reload、10 个旧 loader 回收，以及全部既有清理、分派、调度、卸载、刷新前失败、JPA/事务、Actuator 和终态关闭检查。两个默认配置实际使用 Jackson，Gson 配置断言实际使用 GsonHttpMessageConverter，沿用原报告路径。
- 最终 JAR 直接加载未重编译的 `41a04ab`、`81526a7` zip/sidecar，普通/production 四套验收全部通过业务、20 轮启停、10 轮 reload、10 个旧 loader 回收和失败恢复。旧插件不依赖新夹具；沿用原基线、脚本及报告路径，四套报告的框架 JAR 和旧 ZIP 均与最终磁盘 SHA-256 一致。
- 差异及严格 UTF-8 检查通过。未拆分较大的 manager/smoke 类：本轮只补现有生命周期临界区的一处记账和一个验收阶段，拆分会触及生命周期/验收顺序并扩大兼容性变更面，没有相应收益。
- 未在真实 Java 8 JVM、其它系统或外部现场执行。排空失败仍应显式拒绝释放在用资源，不强制终止任意线程；线程退出后再重试。插件必须在 stop/release hook 等已有回调中释放 initiate 自建资源。已经进入宿主关闭的 manager 仍只允许完成清理，不恢复激活；这些边界及前述自管资源限制未改变。
