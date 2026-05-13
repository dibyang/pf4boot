# 插件加载与打包

## 问题

框架同时支持开发期插件和已打包插件。它必须定位插件路径、读取描述符、选择加载器、构建插件 classloader，并避免将宿主提供的 API 打进插件包。

## 仓库

`Pf4bootPluginManagerImpl.createPluginRepository()` 按以下顺序组合插件仓库：

1. `LinkPluginRepository`：从 `plugins.link` 文件读取插件路径。
2. `Pf4bootPluginRepository`：PF4Boot 特定仓库支持。
3. `ZipPluginRepository`：查找 `.zip` 插件包。
4. PF4J `DevelopmentPluginRepository`：仅 development 模式生效。
5. PF4J `JarPluginRepository`：非 development 模式生效。

`LinkPluginRepository` 删除链接路径时会保留注释和空行，并以 `plugins.link` 文件所在目录解析相对路径。

## 描述符发现

`createPluginDescriptorFinder()` 使用组合 finder：

- PF4J `PropertiesPluginDescriptorFinder`；
- `ManifestPluginDescriptorFinder2`。

`loadPluginFromPath` 处理重复插件 id 时会比较版本。较新的重复插件会卸载旧版本；较旧或相同版本会返回已加载插件。

## 加载器

当配置了 `spring.pf4boot.custom-plugin-loader` 时，`createPluginLoader()` 使用自定义 loader。否则组合使用：

- `JarPf4bootPluginLoader`：非 development 模式下加载 jar 插件；
- `ZipPf4bootPluginLoader`：加载插件 zip 包；
- `Pf4bootPluginLoader`：development 模式下加载开发期插件。

`Pf4bootPluginLoader` 会从 `Pf4bootProperties.classesDirectories` 和 `libDirectories` 添加开发期 class 与 library 目录。

`ZipPf4bootPluginLoader` 会将 zip 包解压到插件根目录旁的 `plugin-cache`，并添加解压后 `lib` 目录中的 jar。

`JarPf4bootPluginLoader` 会添加插件 jar，并通过 PF4Boot 嵌套 jar archive 支持添加 `lib/*.jar`。

## 类加载

内置 loader 都创建 `Pf4bootPluginClassLoader`。它继承 PF4J `PluginClassLoader`，并以宿主应用 classloader 作为 parent。默认策略为 `ClassLoadingStrategy.PDA`，用于避免 API 类重复加载，保持 Spring 按类型注入稳定。

资源行为与类不同：

- `.class` 资源使用正常插件 classloader 行为；
- 普通资源优先从插件 classpath 查找；
- 配置的 plugin-only resource pattern 会强制 `getResources` 只返回插件资源。

## Gradle 打包模型

所有子项目都应用 `net.xdob.pf4boot`。插件模块额外应用 `net.xdob.pf4boot-plugin`。

示例插件使用的依赖作用域：

- `compileOnlyApi`：宿主提供的 API，例如 `pf4boot-api`、`pf4boot-web-support`、`pf4boot-jpa` 和共享 demo library API。
- `bundle`：需要打入插件 zip 的依赖，例如 `pf4boot-jpa-starter`。
- `plugin project(":plugin1")`：`plugin2` 使用的插件间依赖。

`demo-app`、`demo-lib`、`plugin1` 和 `plugin2` 禁用发布。

## 运行时装配

`app-run` 负责装配 demo 应用：

- 将 `runtimeClasspath` 和 `platformClasspath` 中的运行时库放入 `lib`；
- 将 `pluginClasspath` 中的插件 zip 放入 `plugins`；
- 装配 service、config、startup、shutdown 和 package script 资源；
- 通过 Nebula ospackage plugin 生成 RPM 包任务。

## 兼容性

仓库顺序、重复版本处理、loader 适用规则、嵌套 jar 支持或 Gradle 依赖作用域的变更，都可能改变加载或打包结果，应视为运行时兼容性变更。

## 验证

加载和打包变更运行：

- `.\gradlew.bat :pf4boot-core:compileJava`
- `.\gradlew.bat :plugin1:build`
- `.\gradlew.bat :plugin2:build`
- Linux 包装配变化时运行 `.\gradlew.bat :app-run:buildOSPacks`

手动检查应尽可能覆盖从 `plugins.link`、插件 zip 和 demo runtime `plugins` 目录加载插件。
