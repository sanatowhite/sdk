---
name: apptemplate-work
description: "本仓库(android-app-template)的本地开发工作流。用户提出一个改动需求、说"开始改"、"接下来做 X"、或者任何看起来像"写代码"的请求时使用。quick / standard 双模式,AI 推荐 + 用户拍板。"
---

# apptemplate-work — 本仓库的本地开发工作流

改编自 `plaud-native-app2` 的 `plaud-work`(团队三端 monorepo + Jenkins + 飞书 + gbrain + Notion/Linear 的完整工作流)。本仓库是**个人 SDK/模板仓库**,没有团队、没有那些外部系统,但"quick/standard 双模式"、"TDD"、"self code review"、"PR 主题一致性自检"这几条核心工程纪律和团队规模无关,原样保留。

## 触发时第一步:选模式

先看改动类型,给出推荐 + 一句话理由,让用户确认或改选:

| 信号 | 推荐模式 |
|---|---|
| 纯文案/配置/依赖版本号微调,不涉及运行时逻辑 | quick |
| 新增一个 UI 组件/页面、一条业务逻辑分支、一个 Gradle task | standard |
| 涉及 `:updatechecker` 公开 API、`verifyModuleGraph` 允许的依赖图、签名/R8 keep 规则 | standard(且改动前先读 `CLAUDE.md` 对应章节) |
| 不确定 | 默认推荐 standard,理由是"这个仓库的改动大多数会影响到某个模块的公开契约,少数看起来简单的改动最后发现有隐藏耦合" |

## 核心原则

- **PR 主题一致性六列对照表**(见下方 Standard 模式 Step 7)始终强制输出,即使没人 review——它把"顺手改了别的""这些都是同一个文件"这类范围蔓延变成一个可见的产物,自己也不好意思糊弄过去。
- 涉及 `:updatechecker` 的任何改动,先读 `updatechecker/README.md` 的"已知限制 / 不要做的事(四条铁律)"一节。
- 涉及模块依赖的改动,改完先跑一次 `./gradlew verifyModuleGraph` 再做别的事。

## Quick 模式

1. 写最小实现。
2. Self code review(强制,CRITICAL/HIGH 必须修完再继续)。
3. 询问是否要本地装机验证(`installDebug`,默认询问不默认执行——`.debug` 后缀保证不会覆盖手机上的正式版,装了也不影响什么)。
4. 询问是否要本地构建验证(改动小可以跳过;涉及 Gradle 配置、依赖、convention plugin 的改动不建议跳过)。
5. 提交前用 `AskUserQuestion` 问一句"要不要补一条回归测试"——涉及运行时行为默认建议补,纯文案/配置默认可以跳过。
6. PR 主题一致性六列对照表自检(见下)。
7. 询问是否提 PR。

## Standard 模式

TodoWrite 模板(创建后逐条执行,标记进度):

```
1. 写测试 (RED:按改动所在模块覆盖对应层——领域逻辑 success+failure、ViewModel 状态流转 initial→loading→success/failure、UI 组件截图基线) — verify: 新测试全红
2. 写最小实现 (GREEN) — verify: 测试全绿,旧测试无回归
3. 重构 + simplify + 本地 lint(spotlessCheck,detekt 仅参考) — verify: 测试仍绿 + spotless 无 diff
4. 询问是否本地装机验证(installDebug,默认询问不默认执行) — verify: 用户确认或按跳过处理
5. self code review(CRITICAL/HIGH 必须修) — verify: 已修完
6. 本地构建验证:./gradlew test assembleRelease :updatechecker:apiCheck — verify: 全绿
7. PR 主题一致性六列对照表 + 分支前缀检查 + (可选)提 PR — verify: 表格已显式输出,coupling=standalone 时已拆分
8. 合并/收尾后可选:CHANGELOG 补一条 / docs/adr 补一条决策
```

### 各步骤要点

**Step 1(RED)**——按改动所在模块选对应的测试层次:
- `:updatechecker`/`:core-net`/`:core-data` 的领域逻辑 → JUnit4 + Robolectric,success + failure 两条路径都要
- `:app` 的 ViewModel → Turbine 断言状态流转(initial → loading → success/failure),用 MockK 隔离依赖
- `:core-ui` 组件 / `:app` 页面 → Roborazzi 截图基线(新增组件先写一个跑通、再 CI 录制真正的基线,不要本地录制——本地字体渲染和 CI 容器不一致)

**Step 3(重构)**——本地跑 `./gradlew spotlessCheck`,`detekt` 只作参考不阻塞(1.23.x 对 Kotlin 2.4 新语法有解析失败的已知风险,不要因为 detekt 报错就卡住)。

**Step 6(本地构建验证)**——四选一失败就回上一步修,不要带着失败继续:
```bash
./gradlew test assembleRelease :updatechecker:apiCheck
```
如果改动涉及 `:updatechecker`,单独再跑一次 `./gradlew :updatechecker:test :updatechecker:apiCheck`——`apiCheck` 失败是预期的如果确实新增了公开 API,人工确认 diff 只有新增之后再 `apiDump`。

**Step 7(PR 主题一致性六列对照表)**——每次都要显式输出这张表,不能跳过:

| 改动描述 | 涉及文件 | 是否对应本次目标 | 是否跨主题 | 隐含假设 | 耦合度(standalone/coupled) |
|---|---|---|---|---|---|
| ... | ... | ✅/❌ | ✅/❌ | ... | ... |

如果表里出现 `coupling=standalone` 且和本次目标无关的行,先拆出去成单独的改动/PR,不要糊在一起。

同时检查分支命名前缀是否匹配改动类型(`fix/`/`feat/`/`chore/`/`docs/` 等),前缀决定要不要跑全量测试而不是只测触及的模块。

## 本地构建验证命令速查

```bash
./gradlew :updatechecker:test :updatechecker:apiCheck      # 只改了 updatechecker 时
./gradlew assembleDebug testDebugUnitTest lintDebug spotlessCheck verifyModuleGraph   # 全量模板改动
./gradlew :app:assembleRelease                             # R8 + keep 规则相关改动,必须跑一次
./gradlew verifyRoborazziDebug                              # 触及 UI 组件/页面
```

## 装机验证

因为 debug/staging/release 三个 applicationId 后缀互不冲突(`.debug`/`.staging`/无后缀),`installDebug` 不会覆盖手机上已经装的正式版——默认询问但不默认执行,纯粹是因为不是每次改动都需要真机验证,不是因为有风险。

## 不移植的部分(以及为什么)

| plaud-work 原设计 | 本仓库为什么不要 |
|---|---|
| Jenkins 三端实机打包 | 没有团队构建机,本地 `assembleRelease` + 真机/模拟器装机就够 |
| §7.0.0 安全检查邮件审批 | 个人项目,发现问题直接自己决定修不修,不需要走审批链 |
| §7.1 gbrain 中心记忆库沉淀 | 决策直接写 `docs/adr/`,不需要跨项目语义检索 |
| 飞书群通知 / Notion / Linear 联动 | 没有团队协作面,PR 本身的六列表格已经足够留痕 |
| CN/Global 双 flavor 覆盖维度 | 本项目无地区差异,按模块分层覆盖测试即可 |
| 每轮强制的"性能前置评估" | 降级为 Step 3 重构阶段的自查清单(`:core-telemetry` 现成的启动耗时/内存工具正好用来验证),不做成阻塞式产出 |

## Red Flags(看到立刻停下)

- 没写测试就直接改了运行时逻辑代码 → 回到 Step 1
- 改了 `:updatechecker` 的公开签名却没跑 `apiCheck` → 补跑,review diff
- 改了模块间依赖却没跑 `verifyModuleGraph` → 补跑
- `spotlessCheck` 有 diff 却直接提交 → 先 `spotlessApply`
- PR 六列表格里有 `coupling=standalone` 且跟本次目标无关的行,却没有拆分 → 拆分
- 涉及签名/R8/proguard 规则的改动,只跑了 `assembleDebug` 没跑 `assembleRelease` → 补跑(release-smoke 类问题只在 minified 构建暴露)
