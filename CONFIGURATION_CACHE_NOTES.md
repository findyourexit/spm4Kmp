# Configuration Cache (CC) Notes — spm4Kmp

> Upstream issue: #212  
> Status: **Partial, unverified improvement**. Full CC support has NOT been verified
> (`--configuration-cache` has not been run). No `org.gradle.configuration-cache` property
> was changed.

---

## What Was Changed and Why

### 1. `taskGroup`, `cInteropTaskNamesWithProducerTask`, `cInteropTaskNamesWithExportTask` maps — value type changed from `Task` to `TaskProvider<*>`

**Files:**
- `plugin-build/plugin/src/main/java/io/github/frankois944/spmForKmp/SpmForKmpPlugin.kt`
- `plugin-build/plugin/src/main/java/io/github/frankois944/spmForKmp/tasks/ConfigAppleTargets.kt`

**Why it helps CC:**  
Storing a live `Task` instance (obtained via `.get()`) in a map causes Gradle to eagerly realize the
task and hold a reference to the task object across the configuration cache boundary, which prevents
serialisation. Storing `TaskProvider<*>` instead keeps the reference lazy; `dependsOn` and
`mustRunAfter` both accept `TaskProvider`, so the wiring is preserved.

**What was NOT changed:**  
`val outputFiles = definitionTask.get().outputFiles` (ConfigAppleTargets.kt, immediately after
the `definitionTask` registration) still calls `.get()` to read `outputFiles` at configuration
time. This is needed to drive the loop that creates additional cinterop tasks. Removing it would
require a larger refactor (e.g., making `outputFiles` a `ListProperty` derived from a
`@Input`/`@OutputFiles` declaration). Left as-is intentionally.

---

## Remaining CC Blockers

### A. `afterEvaluate {}` in `SpmForKmpPlugin.kt`

**Location:** `SpmForKmpPlugin.kt` — the entire plugin body is wrapped in `afterEvaluate { … }`.

`afterEvaluate` is flagged by Gradle as incompatible with CC because the callback runs during the
configuration phase in a way that is not cache-safe. The fix is to replace it with
`project.pluginManager.withPlugin(…)` hooks or with `project.gradle.projectsEvaluated` /
`lazy configuration` patterns. This is a significant refactor.

### B. Eager task realisation for `outputFiles` at configuration time

**Location:** `ConfigAppleTargets.kt` — `val outputFiles = definitionTask.get().outputFiles`

`definitionTask.get()` at configuration time forces task realisation, a CC anti-pattern. To fix
this, `GenerateCInteropDefinitionTask.outputFiles` would need to be modelled as a
`ListProperty<RegularFile>` (a `Provider`), so the downstream loop can consume it without
realising the task. This is a medium-sized refactor of `GenerateCInteropDefinitionTask`.

### C. `System.getenv(…)` reads at configuration time in `copyPackageResources/ConfigureTask.kt`

**Locations:**
- `ConfigureTask.kt:16` — `System.getenv("BUILT_PRODUCTS_DIR")`
- `ConfigureTask.kt:19` — `System.getenv("CONTENTS_FOLDER_PATH")`
- `ConfigureTask.kt:22` — `System.getenv("PLATFORM_NAME")`
- `ConfigureTask.kt:55` — `System.getenv("EXPANDED_CODE_SIGN_IDENTITY")` / `"EXPANDED_CODE_SIGN_IDENTITY_NAME"`

`System.getenv` reads at configuration time are captured in the CC snapshot; if environment
variables differ between runs the cache entry is valid but the captured value is stale. Gradle
requires such reads to be declared via `providers.environmentVariable(…)` so that they become
tracked `ValueSource` inputs that correctly invalidate the cache when they change.

**Fix:** Replace each `System.getenv("VAR")` with
`project.providers.environmentVariable("VAR").orNull` (configuration time) or inject a
`ProviderFactory` and use it inside the task.

### D. `project.extensions.extraProperties` access at configuration time in `ConfigureTask.kt`

**Location:** `copyPackageResources/ConfigureTask.kt:68` — `project.extensions.extraProperties`

Extra-properties reads should be fine at configuration time (they are part of configuration), but
if the property lookup influences task `enabled` state it can cause problems with CC because
`enabled` is a legacy API. Prefer `@Input` + `onlyIf { … }` for proper CC support.

### E. `project` reference held inside configure lambdas

All the `configureTask(…)` extension functions are called on a `Task` receiver that implicitly
holds `project` (they call `project.isTraceEnabled`, `project.projectDir`, etc.). These calls
happen inside the `tasks.register { … }` action, i.e., at configuration time, so they are
acceptable as long as the values are set into `@Input`/`@Internal` properties and not referenced
again at execution time. A quick audit found no `project` access inside `@TaskAction` bodies —
this is good.

---

## Recommended Sequencing to Reach Full CC Support

1. **Replace `afterEvaluate` (highest impact)** — move all plugin wiring into
   `target.plugins.withId(…)` callbacks or use `target.gradle.projectsEvaluated` in a
   CC-compatible way. This is the single biggest blocker.

2. **Model `outputFiles` as a `Provider<List<RegularFile>>`** — convert
   `GenerateCInteropDefinitionTask.outputFiles` to a `ListProperty<RegularFile>` wired to
   `@OutputFiles`. This eliminates the last `.get()` call in `ConfigAppleTargets.kt` and allows
   the downstream cinterop loop to be driven lazily.

3. **Migrate `System.getenv` to `providers.environmentVariable`** in
   `copyPackageResources/ConfigureTask.kt` — straightforward mechanical change; lets Gradle track
   environment variable changes as CC inputs.

4. **Verify with `--configuration-cache`** — run the full test suite with
   `./gradlew … --configuration-cache` and iterate on any remaining serialisation errors.

---

*These notes were produced by static analysis only. No Gradle build was executed.*
