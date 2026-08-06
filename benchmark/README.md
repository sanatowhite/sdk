# :benchmark

## 这是什么 / 不是什么

`com.android.test` 模块,跑针对 `:app` 的 Macrobenchmark 冒烟测试(`StartupBenchmark`):冷启动 + `StartupTimingMetric`,分别在无编译提示(`CompilationMode.None`)和要求已装 Baseline Profile(`CompilationMode.Partial(BaselineProfileMode.Require)`)两种模式下各跑一次。

**不是**:不是性能门禁。共享 vCPU、无法锁频、无温控的 CI 环境上量出来的绝对耗时数字方差常年 >20%,拿来做时序断言只会产出天天误报、最后被所有人忽略的红灯。这里验证的是**代码不腐烂**(测试能跑通)+ **Baseline Profile 确实生效**(`BaselineProfileMode.Require` 模式下,如果 profile 没装,测试直接 fail——这是唯一机器能可靠断言的事)。真实的性能数字需要在锁频的物理设备上人工测量。

## 独立引入

不适用——这个模块只对 `:app` 有意义(`targetProjectPath = ":app"`),不是可独立抽取的能力。fork 出去的项目要么保留它继续测 `:app`,要么直接删掉(见 `TEMPLATE.md`)。

## 公开 API

无——这是测试模块,不产出被其他模块消费的类。

## 已知限制 / 不要做的事

- **不要**把这里的任何测试结果当成 CI 阻塞门禁。`pr-check.yml` 里没有把它设成 required job——发布前的真实数字验证靠人工在锁频物理设备上跑,不是自动化红绿灯。
- 运行需要 API 28+ 的真机或模拟器(`<profileable android:shell="true"/>` 的前置要求),尽管 `:app` 本身 `minSdk = 24`。
- `connectedBenchmarkAndroidTest` 不支持 Gradle 的 `--tests` 过滤参数(那是 JVM 测试任务的语法)——按测试方法过滤要用 `-Pandroid.testInstrumentationRunnerArguments.class=<全限定类名>#<方法名>`。
- 本模板骨架页面没有真实的滚动/分页 UI,这里只覆盖冷启动这一条路径。真实项目里如果有列表滚动等关键交互,应该补一个对应的 `FrameTimingMetric` 测试。
