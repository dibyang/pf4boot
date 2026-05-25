# 第三阶段运行时安全增强

## 问题

当前运行时管理和释放链路存在几类安全边界不够明确的问题：

- 插件管理接口使用 GET 修改状态，容易被缓存、爬虫或跨站请求误触发；启用管理接口时也缺少默认风险提示。
- `start`、`stop`、`restart`、`reload` 的锁范围分散在内部片段，同一插件被并发操作时可能出现重复启动、启动中停止或 reload 与 stop 交错。
- 动态注册到 root、platform、application 或 MVC 主上下文的 beanName 冲突依赖 Spring 默认异常或覆盖细节，没有框架层的显式策略。
- classloader、scheduled task、MVC mapping、shared bean、`ApplicationContextProvider` 的释放结果缺少稳定的可测观测点。

## 影响模块

- `pf4boot-api`：增加动态 beanName 冲突策略配置和 `ApplicationContextProvider` 观测方法。
- `pf4boot-core`：收敛生命周期操作锁范围，注册共享 bean 前执行冲突策略，暴露定时任务和共享 bean 的测试观测。
- `pf4boot-web-starter`：管理接口改用语义化 HTTP 方法，MVC 动态注册 bean 执行冲突保护。
- `docs/design/en`：同步英文设计说明。

## 设计方案

### 管理接口

管理接口保留读取类 GET：

- `GET /auto-start`
- `GET /list`

状态变更改为语义化方法：

- `PUT /auto-start/{autoStartPlugin}`
- `POST /{pluginId}/enable`
- `DELETE /{pluginId}/enable`
- `POST /{pluginId}/start`
- `DELETE /{pluginId}/start`
- `POST /{pluginId}/restart`
- `POST /{pluginId}/reload`

`all` 仍作为批量操作标记。`pluginAdminEnabled` 默认值暂不改变以保持兼容，但自动配置在启用管理接口时输出安全告警，建议接入鉴权或设置 `spring.pf4boot.plugin-admin-enabled=false` 避免公网暴露。

### 生命周期锁范围

使用现有 `stateLock` 作为生命周期操作锁，覆盖 `startPlugins`、`stopPlugins`、`startPlugin`、`stopPlugin`、`restartPlugin`、`reloadPlugin`、`reloadPlugins` 的状态读取、依赖处理和资源释放。内部的启动/停止核心片段继续使用同一把可重入锁，因此递归启动依赖、停止依赖方不会自锁。

该策略优先保证运行时一致性：同一插件的并发操作被串行化，同时跨插件依赖链在一个生命周期事务中完成。未来如果需要提高跨插件并发度，可在保持依赖图有序加锁的前提下再引入 pluginId 分片锁。

### beanName 冲突策略

新增 `DynamicBeanConflictPolicy`：

- `REJECT`：默认策略。注册前发现同名 singleton 或 bean definition 时直接拒绝，并给出上下文、scope、group 和 beanName。
- `REPLACE`：覆盖前先记录警告，再销毁同名 singleton、移除同名 bean definition，最后注册新 bean。

本阶段不默认采用命名空间化，因为现有公共注册 API 返回 `void`，调用方无法得知最终名称，容易导致停止时按原名注销失败。命名空间化可以作为后续兼容 API 扩展单独设计。

### 释放观测

补齐以下可测断言：

- wrapper classloader 在 unload/reload 后被关闭；
- scheduled task 在 stop 后计数归零；
- MVC mapping 和动态拦截器在 stop/unregister 后计数归零；
- shared bean 在 stop 后从 root/platform/application 移除；
- `ApplicationContextProvider` 在 stop 后不再持有插件 context。

观测方法仅暴露计数或存在性，不暴露内部可变集合。

## 兼容性

管理接口的状态变更路径和 HTTP 方法发生变化，属于运行时管理面的安全性破坏性调整；读取接口仍保持 GET。动态 beanName 冲突默认改为显式拒绝，能更早暴露重复导出、重复 extension 或主上下文同名 controller/interceptor 问题。需要保留旧覆盖行为的应用可配置为 `REPLACE`。

## 验证计划

- `.\gradlew.bat :pf4boot-api:compileJava`
- `.\gradlew.bat :pf4boot-core:test`
- `.\gradlew.bat :pf4boot-web-starter:test`

根构建仍会禁用名称包含 `test` 的任务，因此释放观测断言优先使用模块级 test 任务验证。

## 未决问题

- 管理接口是否应在后续版本默认关闭，需要结合现有用户迁移成本评估。
- 命名空间化 beanName 需要新的返回实际 beanName 的注册 API 或注册记录模型，本阶段不引入。
