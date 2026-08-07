# SPIKE — Hilt library-module `@Module` aggregation (draft notes, to be folded into ADR 0004's revision in Phase 13)

## Question

Does a `@Module @InstallIn(SingletonComponent::class)` declared in a **library** module get
aggregated into `:app`'s Hilt root component if that library module does **not** apply the
Hilt Gradle plugin / KSP `hilt-compiler` itself — i.e. is it enough for the compiled class to
merely be on `:app`'s classpath?

This matters because `telemetry-firebase/build.gradle.kts`'s existing comment claims exactly
that ("真正的 Hilt 聚合发生在 `:app`，这个模块的 class 只要在 `:app` 的 classpath 上就会被聚合"),
and the entire "`-hilt` companion module" design (core-common-hilt, core-data-hilt,
core-telemetry-hilt, net-telemetry-hilt, telemetry-firebase) depends on knowing whether that's
true.

## Method

Built two throwaway library modules, both with one `@Module @InstallIn(SingletonComponent::class)`
contributing `@Provides @IntoSet fun ...: String`, both added as `implementation(project(...))`
to `:app`:

- **`:spike-hiltlib-a`** — applies only `sanato.android.library` (no Hilt Gradle plugin, no KSP
  `hilt-compiler`). Only `dagger-hilt-android` (annotations) on the classpath.
- **`:spike-hiltlib-b`** — applies `sanato.android.library` + `sanato.android.hilt` (Hilt Gradle
  plugin + KSP `hilt-compiler`), identical in every other respect.

Ran `:app:hiltJavaCompileDebug --rerun` (after clearing `app/build/generated/{hilt,ksp}` to force
a clean regeneration) and inspected the generated root component sources:
`app/build/generated/hilt/component_trees/debug/.../AppTemplateApp_ComponentTreeDeps.java` and
`app/build/generated/hilt/component_sources/debug/.../AppTemplateApp_HiltComponents.java`.

## Result

```
$ grep -n -i spike AppTemplateApp_ComponentTreeDeps.java
58:  import hilt_aggregated_deps._spike_hiltlib_b_SpikeModuleB;
118:        _spike_hiltlib_b_SpikeModuleB.class

$ grep -n -i spike AppTemplateApp_HiltComponents.java
60:   import spike.hiltlib.b.SpikeModuleB;
146:           SpikeModuleB.class,
```

**`SpikeModuleB` (Hilt plugin + KSP applied) is aggregated. `SpikeModuleA` (classpath-only,
no Hilt plugin/KSP) is aggregated is not present anywhere in the generated component — no error,
no warning, nothing.** `spike-hiltlib-a`'s own build output confirms there was never even a
`kspDebugKotlin` task for that module (no KSP applied → no `hilt_aggregated_deps` proto class
ever generated for it), so there was nothing for `:app`'s aggregating step to find.

Independently confirmed the failure mode is **silent, not a compile error**: `:app` built and
`hiltJavaCompileDebug` succeeded with `SpikeModuleA` completely un-aggregated — no diagnostic
at any point.

## Conclusion

**The `telemetry-firebase/build.gradle.kts` comment is wrong, and always has been** (this path
was never exercised — `telemetryFirebaseEnabled` defaults to `false` and nothing in CI/scripts
ever flips it). Confirmed as a real, if currently dormant, bug: today, flipping
`telemetryFirebaseEnabled=true` would silently produce an app with **no** Firebase telemetry
backend — `FirebaseTelemetryModule` would compile fine but never be installed, and because
`TelemetryBackendsModule.telemetryBackends()` is `@Multibinds`, there is no missing-binding
compile error to surface it.

**Decision for the rest of this plan: every `-hilt` companion module (`core-common-hilt`,
`core-init-hilt`, `core-data-hilt`, `core-telemetry-hilt`, `net-telemetry-hilt`,
`telemetry-firebase`) must apply `sanato.android.hilt`** (Hilt Gradle plugin + KSP
`hilt-compiler`), not just carry `@Module` classes on the classpath. `telemetry-firebase`'s
build file and its explanatory comment both need to be corrected as part of Phase 7.

This note is scratch working material for this plan, not a numbered ADR — Phase 13 folds this
finding into the ADR 0004 revision ("Hilt + KSP DI boundary") and can delete this file once that
lands.
