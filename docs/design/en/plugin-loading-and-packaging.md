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
- `plugin project(":samples:cross-plugin-jpa:plugin-user-book-service")`: plugin-to-plugin dependency used by the sample workflow plugin.

Publishing is disabled for the sample host, model, and plugin modules under `samples/cross-plugin-jpa`.

## Runtime Assembly

The old root-level `app-run` demo assembly module has been removed. The current runnable sample is handled by modules under `samples/cross-plugin-jpa`:

- `demo-host:assembleSamplePlugins` collects sample plugin zips into the demo host `build/sample-plugins` directory;
- `demo-host:runSampleHost` starts the host and loads plugins from the sample plugin directory;
- `app-run:assembleSampleRuntime` assembles a runnable layout with `lib`, `plugins`, `config`, and `bin`;
- `app-run:sampleDistZip` creates the sample distribution zip;
- Linux distribution assembly is no longer maintained by a root demo module.

## Hot Replacement Package Paths

Phase-one hot replacement does not change the public plugin repository contract and does not require migration of `plugin-cache`, `plugins.link`, or the sample runtime layout. `PluginDeploymentService` receives a prepared `stagedPluginPath` and reads the staged package descriptor during precheck.

Current package-handling boundary:

- The staged package must already be fully written before `replace(...)` is called, and it must be parseable by the current `PluginDescriptorFinder`.
- The staged package plugin id must match the target plugin id.
- Old package paths for the impact chain are captured from current `PluginWrapper.getPluginPath()` into `RollbackSnapshot`.
- On replacement failure, the deployment service reloads old package paths and restores the original started state.
- `staged/backup/failed` are internal deployment-service concepts in phase one, not a new public plugin repository format.

Recommended runtime layout:

```text
plugins/
  active/
  staged/
  backup/
  failed/
```

Constraints:

- Operations flows are responsible for placing candidate packages in `staged/` and completing validation, permissions, and atomic write before execution.
- After success, staged packages may be cleaned according to operations policy. Failed candidates should move to `failed/` together with the deployment record.
- If directory switching becomes framework-owned later, extend the deployment service package activation step first instead of changing the loader's default repository scan order.

## Compatibility

Changes to repository order, duplicate version handling, loader applicability, nested jar support, or Gradle dependency scopes can change what gets loaded or bundled. Treat them as runtime compatibility changes.

## Verification

For loading and packaging changes, run:

- `.\gradlew.bat :pf4boot-core:compileJava`
- `.\gradlew.bat :pf4boot-core:test`
- `.\gradlew.bat :samples:cross-plugin-jpa:demo-host:assembleSamplePlugins`
- `.\gradlew.bat :samples:cross-plugin-jpa:app-run:sampleDistZip`
- `.\gradlew.bat :samples:cross-plugin-jpa:demo-host:runSampleHost`

Manual checks should cover loading from `plugins.link`, a plugin zip, and the sample host plugin directory when possible.
