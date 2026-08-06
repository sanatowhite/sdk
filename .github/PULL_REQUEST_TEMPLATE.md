## What / why

<!-- One or two sentences. What changed, and why — not a restatement of the diff. -->

## PR 主题一致性六列对照表

<!-- 见 .claude/skills/apptemplate-work/SKILL.md ——即使自己 approve 自己,也要显式填这张表。 -->

| 改动描述 | 涉及文件 | 是否对应本次目标 | 是否跨主题 | 隐含假设 | 耦合度(standalone/coupled) |
|---|---|---|---|---|---|
| | | | | | |

## Checklist

- [ ] `./gradlew spotlessCheck lintDebug verifyModuleGraph` passes locally
- [ ] Touches `:updatechecker`? → ran `:updatechecker:test :updatechecker:apiCheck`, reviewed the API diff is additive-only
- [ ] Touches signing / R8 / proguard rules / navigation routes? → ran `:app:assembleRelease`, not just `assembleDebug`
- [ ] Touches a `core-*` module's dependencies? → `verifyModuleGraph` still passes
- [ ] Touches Compose UI? → `verifyRoborazziDebug` passes, or baselines were re-recorded via `screenshot-record.yml` and the PNG diffs were reviewed
