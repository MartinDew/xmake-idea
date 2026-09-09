# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

An IntelliJ Platform plugin (`io.xmake`, published as "XMake") that integrates the
[xmake](https://github.com/xmake-io/xmake) build system into IntelliJ IDEA and CLion. It provides an
`xmake.lua` language (lexer/parser/highlight/completion), a run configuration type, named build
profiles surfaced as execution targets, a build/run/debug toolbar and menu, project wizards, a tool
window console with compiler-diagnostic parsing, and optional CLion-only C/C++ debugging, native
"Xmake Executable" run configs, Custom Build Targets, and compile-database IntelliSense.

## Build & test commands

The Gradle wrapper drives everything; the `scripts/*.sh` wrappers are what CI runs.

- `./gradlew build` — compile + assemble (CI: `./scripts/build.sh`, which also runs `buildPlugin`)
- `./gradlew buildPlugin` — produce the distributable ZIP at `build/distributions/xmake-idea.zip`
- `./gradlew runIde` — launch a sandbox CLion with the plugin loaded (CI: `./scripts/run.sh`)
- `./gradlew verifyPlugin` — IntelliJ Plugin Verifier compatibility check (CI: `./scripts/verify.sh`)
- `./gradlew :compileKotlin` / `./gradlew :clion-debug:compileKotlin` — fast per-module compile
- There is currently **no test source set** (`src/test` does not exist); `./gradlew test` is a no-op.

### Toolchain constraints

- **JDK 25 is required.** Both `build.gradle.kts` and `clion-debug/build.gradle.kts` set
  `jvmToolchain(25)`; the foojay resolver in `settings.gradle.kts` auto-provisions it, so a local
  build downloads JDK 25 on first run regardless of the installed Java version.
- Kotlin `2.3.20`, IntelliJ Platform Gradle Plugin `2.x`. `pluginSinceBuild` and `runIdeVersion`
  (the CLion build the plugin resolves and runs against) are in `gradle.properties`. The build
  resolves against **CLion**, not IDEA.
- Line endings: `.gitattributes` forces LF for all sources; `*.bat`/`*.cmd` are CRLF. Indent with
  4 spaces (see `CONTRIBUTING.md`).
- Contributions target the `dev` branch (merged to `master` later). Commit messages in English.

## Module layout

Two Gradle modules:

- **root** (`src/main/kotlin/io/xmake/...`) — the whole plugin. Compiles against the IntelliJ
  Platform + CLion, but must **load and function on IDEA Community** where CLion/CIDR classes are
  absent. Never import `com.jetbrains.cidr.*` or CLion-plugin classes here.
- **`:clion-debug`** (`clion-debug/`) — an *optional content module*
  (`<module name="xmake-idea.clion-debug" loading="optional"/>` in `plugin.xml`), built as a real
  Gradle project (`compileOnly(project(":"))`, packaged via `pluginModule(project(":clion-debug"))`
  → `lib/modules/xmake-idea.clion-debug.jar`). It is the **only** code allowed to touch
  `com.jetbrains.cidr.*`, `com.intellij.clion-compdb`, `com.intellij.nativeDebug`, and
  `intellij.cidr.debugger.core`.

### The CLion boundary (important)

The root module has no compile-time dependency on `:clion-debug`. All CLion-only behavior is
reached through **two extension points** the root module declares and `:clion-debug` implements in
its own `xmake-idea.clion-debug.xml`:

| EP (`io.xmake.*`) | Root interface | CLion impl | Purpose |
|---|---|---|---|
| `debugSupport` | `io.xmake.debug.XMakeDebugSupport` | `ClionDebugSupport` | build an `XDebugProcessStarter` for a resolved `XMakeDebugLaunch` |
| `clionSupport` | `io.xmake.clion.XMakeClionSupport` | `ClionSupport` | register the native run-config type, sync Custom Build Targets, attach compile_commands |

`XMakeDebugSupport.find()/isAvailable()` and `XMakeClionSupport.find()/isAvailable()` return
`null`/`false` on IDEA Community, and every caller degrades to a no-op. When adding a CLion-only
capability: add a method to the relevant root interface, implement it in `:clion-debug` (delegating
to an `*Integration` object that guards with a `Class.forName` `isAvailable()` check), and never
import CLion classes from root. Data crossing the boundary uses plain public classes in
`io.xmake.clion` / `io.xmake.debug` (e.g. `XMakeBuildTargetSpec`, `XMakeDebugLaunch`) — not JSON,
since `:clion-debug` compiles against root.

## Core architecture

### Build profiles — the configuration model

- **`XMakeBuildProfile`** (`project/profile/`) is a persisted, named bundle of shared build inputs:
  toolkit id, platform/architecture/toolchain, build mode, working/build directories, Android NDK
  path, verbose, configure arguments. It has a stable `id` and `canExecute(project)` /
  `resolveToolkit` / `resolveWorkingDirectory` helpers.
- **`XMakeBuildProfileManager`** (`@Service`, `xmake.xml`) owns the profile list, normalizes/validates
  on load, guarantees a default profile, and publishes `XMakeBuildProfileManager.TOPIC` on change.
  Accessed via `project.xmakeBuildProfiles`.
- Profiles are published as **execution targets** (`run/target/XMakeBuildProfileExecutionTarget` +
  `…Provider`). `XMakeExecutionTargetSyncActivity` keeps the run-toolbar target and each run
  configuration's `preferredBuildProfileId` in sync. `Project.activeOrSingleXMakeBuildProfile`
  resolves the profile in effect.
- **`XMakeRunConfiguration`** (`run/`) now only carries *launch* settings (target, arguments,
  environment, DAP driver, launch JSON) plus `preferredBuildProfileId`. Legacy run configs with
  embedded build settings are migrated to profiles by `migration/XMakeBuildProfilesConverterProvider`
  (project-load converter) with a runtime fallback in `readExternal`.

### Command execution

- **`XMakeCommandFactory(project, profile)`** (`run/command/`) is the single place that turns a
  profile into `xmake` invocations: `createConfigure/createBuild/createTargetBuild/createRebuild/
  createClean/createRun/createTargetPathQuery/createInfoQuery/createUpdateCompileCommands/…`. Each
  returns an `XMakeCommand` (command line + resolved `toolkit` + `workingDirectory`).
- Every profile gets an isolated xmake config directory via `XMAKE_CONFIGDIR`:
  `.idea/xmake/profiles/<profileId>/<configHash>` (`XMakeCommandBuilder.forBuildProfile`).
- **`XMakeExecutionService`** (`@Service`, `run/command/`, `project.xmakeExecutionService`) runs
  commands under a single `Mutex` (`runExclusive` / `submit`) so configure/query/build sequences
  never interleave. **The mutex is not reentrant** — never call `withProfileCommands` /
  `runExclusive` from inside another. `withProfileCommands(profile) { execService -> … }` is the
  standard "do a bunch of commands for one profile" wrapper.
- `run/state/XMakeRunState` + `XMakeDebugState` build the command set for a launch;
  `XMakeRunner` (an `AsyncProgramRunner`) executes them. Plain builds route through
  `build/XMakeProjectTaskRunner` (a `ProjectTaskRunner` fed `XMakeBuildTask`s by the build actions).

### Toolkits (where xmake actually runs)

- **`Toolkit`** = a resolved `xmake` binary on a host (LOCAL / WSL / SSH); `id` is derived from the
  physical host + path so registrations survive rescans. `requiresBackend` ⇒ WSL/SSH.
- **`ToolkitScanner`** discovers installs, **`ToolkitRegistry`** owns user registrations + the
  default, **`ToolkitManager`** combines them and exposes project-aware `registeredToolkit(id,
  project)` / `registeredToolkits(project)` / `visibleToolkits`. Changes fire
  `ToolkitListener.TOPIC` (app-level).
- **`utils/execute/CommandEx.kt`** — `GeneralCommandLine.createProcess(toolkit, project, workDir)`
  dispatches by host type (local / WSL / SSH via the `io.xmake.toolkitHostExtension` EP, `KEY ==
  "SSH"`, shipped in the optional `META-INF/io.xmake-ssh.xml`).

### xmake info probing

`XMakeInfoManager` (project service) runs `xmake show -l <key> --json` **in the active profile's
command context** for architectures/buildmodes/platforms/targets/toolchains/apis, debounced, and
re-probes on `ToolkitListener.TOPIC`, `XMakeBuildProfileManager.TOPIC`, or an `xmake.lua` VFS
change. It publishes `XMakeInfoManager.XMAKE_INFO_TOPIC` and feeds `xmake.lua` completion
(`XMakeLuaLexer.updateApis`). `project/profile/XMakeBuildProfileOptions*` caches per-profile option
lists for the profile editor.

### CLion integration (root side, `io.xmake.clion`)

- `XMakeClionActivity` (startup): registers the native run-config type via `XMakeClionSupport`, then
  drives `XMakeClionTargetSync` on `XMAKE_INFO_TOPIC` / `XMakeBuildProfileManager.TOPIC` /
  `ExecutionTargetManager.TOPIC`.
- `XMakeClionTargetSync` (`@Service`, debounced): for the active profile, discovers targets
  (`project/target/discoverXMakeBuildTargets`), resolves each executable path
  (`project/target/resolveXMakeTargetPath` → `xmake l scripts/targetpath.lua`, `__begin__…__end__`),
  builds an `XMakeBuildTargetSpec`, and calls `XMakeClionSupport.syncBuildTargets` + refreshes
  compile_commands. LOCAL toolkits only.
- `:clion-debug` side: `CustomBuildTargetsIntegration` + `CLionBuildTargetRegistrar.java` (Java,
  because `CLionProjectToolManager` is Kotlin-`internal`) install CLion Custom Build Targets and
  ready-to-run `XMakeExecutableRunConfigurationType` configs; `CompDBIntegration` links the
  compilation database.

### Console / tool window

Bottom tool window `XMake` (`XMakeToolWindowFactory`) with an output panel and a problem panel.
`XMakeConsoleService` (project service) owns the single `XMakeConsole`; get it via
`xmakeConsoleService.whenReady { … }` (callback) or `awaitReady()` (suspend) — the tool window
initializes lazily. `run/command/XMakeCommandProcessHandler` streams output and parses diagnostics
(`SystemUtils.parseProblem`, several compiler families).

### Actions

`actions/XMakeProjectAction` (base) → visible only when `SystemUtils.isXMakeProject`. `XMakeBuildAction`
builds an `XMakeBuildTask` and hands it to `ProjectTaskManager`; `XMakeCommandAction` runs commands
straight on the execution service. Build/run/debug actions hide while a native "Xmake Executable"
config is selected (`hideForSelectedXMakeExecutableConfig` / `SystemUtils.isXMakeExecutableConfigSelected`).
`RunToolbarBuildAction` adds a build button to the run widget; `EditXMakeBuildProfilesAction` opens
the profile editor from the menu and the execution-target popup.

### Debug flow (CLion only)

`XMakeRunner.execute` (debug executor) → `XMakeDebugState` → `debug/prepareXMakeDebugLaunch`: warns
on a non-debug build mode, runs configure + `xmake build <target>`, resolves the binary via
`createTargetPathQuery`, picks a DAP driver (`DapDriverDetector`, lldb-dap / gdb ≥ 14.1), and
returns an `XMakeDebugLaunch`. `XMakeRunner` then asks `XMakeDebugSupport` for an
`XDebugProcessStarter`. `ClionDebugSupport` builds an **in-process `CidrLocalDebugProcess`** with
`XMakeDapDriverConfiguration` (CLion's own debugger UI — disassembly/registers/memory — no separate
external adapter setup). Remote toolkits are rejected upstream in `XMakeDebugState.create`.

### `xmake.lua` language

`file/` package: `XMakeLuaFileType` (bound to files named `xmake.lua`), `XMakeLuaLanguage`, plus
`highlight/` (hand-written `XMakeLuaLexer`), `parser/`, and `completion/XMakeLuaCompletionContributor`.

## Conventions

- Source files carry the Apache-2.0 header block (`@file` / `@author ruki` on older files).
- Project/app **services** are accessed via Kotlin extension-property helpers next to their
  declaration: `project.xmakeBuildProfiles`, `project.xmakeExecutionService`,
  `project.xmakeConsoleService`, `project.xmakeSettings`, `ToolkitManager.getInstance()`.
- Cross-module data types crossing the CLion boundary live in root (`io.xmake.clion`, `io.xmake.debug`)
  and stay plain public classes.
- Logging: root uses `io.xmake.utils.Logger` or `com.intellij.openapi.diagnostic.Logger`;
  `:clion-debug` has its own `io.xmake.debug.clion.utils.Logger`.
- Persisted state classes: mutable `var`s with defaults + no-arg construction for `XmlSerializer`;
  `XMakeBuildProfile` relies on data-class equality for the profile editor's modified-check.
