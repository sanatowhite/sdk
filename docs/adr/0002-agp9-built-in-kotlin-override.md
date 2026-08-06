# 0002 — AGP 9 built-in Kotlin, with Kotlin/KSP versions overridden via `buildscript`

## Context

AGP 9 makes the Kotlin Gradle Plugin a runtime dependency of AGP itself ("built-in Kotlin") rather than something each module applies via `org.jetbrains.kotlin.android`. AGP 9.3.1 ships with KGP 2.2.10 built in. Hilt 2.60.1's POM requires `symbol-processing-api` 2.3.7 and `kotlin-stdlib` 2.3.21 — i.e. Kotlin ≥ 2.3, which the built-in 2.2.10 does not satisfy. There is no supported way to raise the built-in Kotlin version through `plugins {}` block version pinning.

Separately: KSP had a real incompatibility with AGP 9's built-in-Kotlin model (tracked as google/ksp#2615, requiring `android.builtInKotlin=false` as a workaround) until KSP 2.3.6 removed the dependency causing it, and 2.3.10 fixed R-class resolution under AGP 9. Kotlin 2.4.x's module-name handling (colons in module names) also requires KSP ≥ 2.3.10.

## Decision

- Override the Kotlin version repo-wide via the root `build.gradle.kts`'s `buildscript { dependencies { classpath(...) } }` block — **not** via `plugins {}`, which cannot override AGP's built-in version. Pinned to Kotlin 2.4.10.
- Pin KSP to 2.3.11 (above the 2.3.10 floor required for both the AGP 9 R-class fix and Kotlin 2.4's module-name fix).
- Remove `org.jetbrains.kotlin.android` from every module — it is no longer applied anywhere; Kotlin compilation is provided by AGP itself.
- Keep applying `org.jetbrains.kotlin.plugin.compose` explicitly wherever Compose is used — built-in Kotlin does not replace the Compose compiler plugin, that remains a separate, required `apply`.
- Do **not** set `kotlin.compilerOptions.jvmTarget` anywhere; it now defaults to `compileOptions.targetCompatibility`, removing one source of drift between the two settings.
- Confirmed via `javap -public` bytecode inspection and a real Hilt+KSP+Compose probe build (Phase 0.5) that this combination compiles and runs `@HiltAndroidApp`/`@AndroidEntryPoint`/KSP-generated code correctly, without needing `android.builtInKotlin=false`.

## Consequences

- The dependency chain is fragile in one specific way: any future Hilt upgrade that raises its minimum Kotlin requirement again may require bumping the `buildscript` override, and any KSP downgrade below 2.3.10 will silently reintroduce the R-class/module-name bugs this pin avoids. Both floors are documented inline in `gradle/libs.versions.toml` comments, not just here.
- `:updatechecker` deliberately never applies any convention plugin and stays on its own minimal build file — this whole override mechanism has zero effect on it, which is intentional (see ADR 0003).
- Because KSP+built-in-Kotlin+Hilt was, at the time this was built, a recently-fixed and not widely battle-tested combination, `:app` was kept as the *only* module doing KSP-based Hilt code generation — narrowing the blast radius of this specific compatibility risk to one module instead of spreading it across the whole graph.
