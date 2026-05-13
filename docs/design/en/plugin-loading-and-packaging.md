# Plugin Loading And Packaging

## Problem

The framework supports both development-time plugins and packaged plugins. It must locate plugin paths, read descriptors, choose a loader, build plugin class loaders, and keep host-provided APIs out of plugin bundles.

## Repositories

`Pf4bootPluginManagerImpl.createPluginRepository()` composes repositories in this order:

1. `LinkPluginRepository`: reads plugin paths from `plugins.link` files.
2. `Pf4bootPluginRepository`: PF4Boot-specific repository support.
3. `ZipPluginRepository`: finds `.zip` plugin packages.
4. PF4J `DevelopmentPluginRepository`: active in development mode.
5. PF4J `JarPluginRepository`: active outside development mode.

`LinkPluginRepository` preserves comments and blank lines when deleting linked paths and resolves relative paths from the `plugins.link` file location.

## Descriptor Discovery

`createPluginDescriptorFinder()` uses a compound finder:

- PF4J `PropertiesPluginDescriptorFinder`;
- `ManifestPluginDescriptorFinder2`.

During `loadPluginFromPath`, duplicate plugin ids are handled by comparing versions. A newer duplicate unloads the older one; an older or equal duplicate returns the already loaded plugin.

## Loaders

`createPluginLoader()` uses a custom loader when `spring.pf4boot.custom-plugin-loader` is configured. Otherwise it composes:

- `JarPf4bootPluginLoader` for packaged jar plugins outside development mode;
- `ZipPf4bootPluginLoader` for plugin zip packages;
- `Pf4bootPluginLoader` for development plugins.

`Pf4bootPluginLoader` adds configured development class and library directories from `Pf4bootProperties.classesDirectories` and `libDirectories`.

`ZipPf4bootPluginLoader` expands zip packages into the manager's sibling `plugin-cache` directory and adds jars from the extracted `lib` directory.

`JarPf4bootPluginLoader` adds the plugin jar and nested `lib/*.jar` entries through PF4Boot's nested jar archive support.

## Class Loading

All built-in loaders create `Pf4bootPluginClassLoader`. It extends PF4J `PluginClassLoader` and uses the host application class loader as parent. The default strategy is `ClassLoadingStrategy.PDA`, chosen to avoid duplicate API classes and keep Spring autowiring by type stable.

Resource behavior differs from classes:

- `.class` resources use normal plugin class loader behavior;
- plain resources are searched in the plugin classpath first;
- configured plugin-only resource patterns force `getResources` to return only plugin resources.

## Gradle Packaging Model

All subprojects apply `net.xdob.pf4boot`. Plugin modules additionally apply `net.xdob.pf4boot-plugin`.

Dependency scopes used by sample plugins:

- `compileOnlyApi`: host-provided APIs such as `pf4boot-api`, `pf4boot-web-support`, `pf4boot-jpa`, and shared demo library APIs.
- `bundle`: dependencies packaged into the plugin zip, such as `pf4boot-jpa-starter`.
- `plugin project(":plugin1")`: plugin-to-plugin dependency used by `plugin2`.

Publishing is disabled for `demo-app`, `demo-lib`, `plugin1`, and `plugin2`.

## Runtime Assembly

`app-run` assembles the demo application:

- runtime libraries from `runtimeClasspath` and `platformClasspath` into `lib`;
- plugin zip artifacts from `pluginClasspath` into `plugins`;
- service, config, startup, shutdown, and package script resources;
- RPM package tasks through the Nebula ospackage plugin.

## Compatibility

Changes to repository order, duplicate version handling, loader applicability, nested jar support, or Gradle dependency scopes can change what gets loaded or bundled. Treat them as runtime compatibility changes.

## Verification

For loading and packaging changes, run:

- `.\gradlew.bat :pf4boot-core:compileJava`
- `.\gradlew.bat :plugin1:build`
- `.\gradlew.bat :plugin2:build`
- `.\gradlew.bat :app-run:buildOSPacks` when Linux package assembly changes

Manual checks should cover loading from `plugins.link`, a plugin zip, and the demo runtime `plugins` directory when possible.
