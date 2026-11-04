# Java 8 与 Linux 运行验收

[English](en/java8-linux-acceptance.md)

## 范围与方案

- Windows 使用用户指定的 `C:\Program Files\Java\jdk-1.8`（8u431）构建及运行，Linux 使用用户授权主机已有 OpenJDK 8u312；不替换系统 Java，不部署到现有服务。
- 复用 RuntimeSmokeRunner、既有 JUnit 和两组未重编译旧插件。标准库脚本仅导出独立 classpath、校验制品 SHA-256、启动隔离进程并收集结果；不新增框架依赖或生命周期组件。
- 导出只包含运行制品、测试类/资源/依赖与旧插件，不包含 .git、SSH 配置、凭据或用户目录。Linux 使用独立 /tmp 目录，服务绑定回环独立端口，只终止自己启动的进程。
- 同一封存包分别验证 Windows/Linux 的真实 Java 8、各模块测试、普通/production/Gson 完整业务与异常清理、20 轮启停、10 轮 reload/loader 回收及四套旧插件升级。
- 公共 API、插件格式及默认配置不变；验收通过后白名单暂存本线程积累的资源清理修复及验收文件，本地提交并推送，不执行发布或部署。

## 执行

```powershell
.\gradlew.bat '-Dorg.gradle.java.home=C:\Program Files\Java\jdk-1.8' build :samples:cross-plugin-jpa:app-run:runtimeSmoke :samples:cross-plugin-jpa:app-run:runtimeSmokeProduction
.\gradlew.bat '-Dorg.gradle.java.home=C:\Program Files\Java\jdk-1.8' -I samples/cross-plugin-jpa/app-run/src/smoke/acceptance/export.init.gradle exportJvmAcceptance
```

导出目录 `build/jvm-acceptance` 必须是新目录，避免混用旧产物。对其中 `verify.py --seal` 生成封存哈希，传输整个封存包；目标执行 `python3 verify.py --java /usr/bin/java --port 17811`。Windows 使用相同脚本并显式指定 Java 8 java.exe。

## 结果

最新的释放钩子追加边界复验已在两端各通过 366 项 JUnit、三套宿主和四套旧包升级；该轮封存哈希、报告及兼容性修正见 [追加钩子验收记录](plugin-resource-cleanup.md#追加钩子验收记录)。下表是 `4ef8284` 的前轮验收记录，保留作为历史证据。

2026-11-04，最终封存候选 `build/jvm-acceptance-complete` 两端全部通过。本记录随本线程资源清理修复提交，提交标识以 Git 日志为准。

| 平台 | 真实 JVM | 模块测试 | 真实宿主 | 旧插件升级 |
| --- | --- | --- | --- | --- |
| Windows 11 x86_64 | Oracle Java 8u431 | 9 模块 / 364 项通过 | 普通、production、Gson 全通过 | 两组旧包 × 两配置全通过 |
| Linux 4.18 x86_64 | OpenJDK 8u312 | 9 模块 / 364 项通过 | 普通、production、Gson 全通过 | 两组旧包 × 两配置全通过 |

- 每套真实宿主包含 31 轮 DTO GET/POST、20 轮启停、10 轮 reload 和 10 个旧 loader 回收，以及全部排空、启动/销毁失败重试、跨插件分派、调度回滚、卸载/关闭、JPA 与 Actuator 场景。每套旧包验收包含业务、20 轮启停、10 轮 reload/loader 回收和异常恢复。
- 旧包基线为 `41a04ab`、`81526a7`，直接使用此前保留的 4 ZIP/sidecar，不重新编译旧插件。两端各 16 个检查（9 个模块 + 3 套宿主 + 4 套升级）均为 PASSED，JUnit 数量与完成的 Gradle 构建核对一致。
- Windows 完整构建使用真实 JDK 8 编译、测试；Linux 运行同一批编译字节码及依赖，不宣称在 Linux 上重新解析依赖或构建源码。
- 最终压缩包 SHA-256：`620c7f275a28014a0c846e8a36903fe6fcb5bfa7231d2bf6e5fb5a7f4071c6d3`。两端输入清单 SHA-256 相同：`9ee1f66353c53bdc63d1253916a50f32272dace7d31c242766660c77347d580a`；执行后 848 个封存输入全部未变。两端验收端口 17816 均已释放。
- Windows 报告：`build/jvm-acceptance-complete/reports/result.json`；Linux 取回报告：`build/linux-java8-evidence/reports/result.json`。详细 JUnit 日志、每套宿主 JSON/XML 和旧包日志均在对应 reports 目录。构建产物和报告不纳入 Git。

## 本轮修正

没有因平台差异新增框架运行时机制。真实验收发现并修正以下测试/导出问题，所有失败候选及本地日志保留，不以重试绿灯代替定位：

1. 并发关闭测试不能依赖 JVM 瞬时 Thread.State；改为关闭屏障未释放时 Future 必须超时、释放后启动明确拒绝，保留互斥保护。
2. 宿主就绪需同时等待 workflow 和 unrelated 必需入口，避免异步插件尚未启动就把 404 当作失败。
3. Python 回环请求显式不使用代理，避免 Windows 系统代理未绕过 127.0.0.1 导致旧包检查连接不到实际服务；系统代理未改动。
4. 应用文件日志定位到 reload 时扩展测试端口重绑冲突。新夹具避免随机 socket 抢占扩展的固定端口；压力测试不依赖复用相同端口数字，直接检查旧 socket 已关闭，再执行真实 reload，保留 loader 回收和异常清理断言。旧插件 ZIP 不变。
5. 导出不可变输入时必须包含 production 离线 repository；脚本增加仓库输入、旧 ZIP 数量、模块及 JUnit 覆盖数量前置/一致性检查，不降低 production 预检查。运行日志、数据目录不导出。

重导出新候选可使用 `-PacceptanceDirectory=jvm-acceptance-complete` 指定 build 下的新目录。只在首次执行前 `--seal`，不得对运行后的目录重新封存来掩盖输入变化。Linux 使用已有低权限 nobody 用户，未安装软件或改变既有服务。验收只覆盖上述 JVM/架构和既有夹具，不保证任意第三方自管资源的释放。
