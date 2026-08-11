# :net-telemetry-hilt

## 这是什么 / 不是什么

`:core-net` 与 `:core-telemetry` 之间唯一的粘合桥:把 `NetworkMetricsSink`(`core-net` 的接口)接到 `Telemetry.networkRequest`(`core-telemetry` 的方法),以及把 `WebSocketMetricsSink`(`core-net` 的 `ws` 子包的接口)接到 `Telemetry.eventIfEnabled`。

**不是**:不含任何网络或遥测的实际实现——那些在各自的模块里。这两个 core 模块彼此零依赖是有意的设计,粘合只应该发生在这里。

## 一行接入

只有**同时**用了 `:core-net` 和 `:core-telemetry-hilt` 的消费方才需要它:

```kotlin
dependencies {
    implementation("com.github.sanatowhite.sdk:core-net:1.0.0")
    implementation("com.github.sanatowhite.sdk:core-telemetry-hilt:1.0.0")
    implementation("com.github.sanatowhite.sdk:net-telemetry-hilt:1.0.0")
}
```

加上这一行,`HttpClientFactory.okHttpClient(metricsSink = ...)` 就能注入现成的 `NetworkMetricsSink`,网络请求耗时自动进 `Telemetry.networkRequest`;同理 `WebSocketFactory.create(metricsSink = ...)` 能注入现成的 `WebSocketMetricsSink`,长连接的打开/重连/关闭事件自动进 `Telemetry.event()`(走 `ws_opened`/`ws_reconnecting`/`ws_closed`/`ws_failed` 四个事件名)。只用网络栈、不用遥测(或反过来)的消费方不需要这个模块。

## AI 接入指南(可直接执行)

**要不要用这个模块**:只有**同时**依赖 `:core-net` 和 `:core-telemetry-hilt` 才需要;任一边缺失都不需要加。

**接入步骤**:
1. 加坐标(见上方"一行接入"的三行)。
2. 组装 OkHttp 客户端的地方,注入 `NetworkMetricsSink` 并传给 `HttpClientFactory.okHttpClient(metricsSink = sink)`。

**验证**:发起一次网络请求,确认 `Telemetry.networkRequest` 事件出现在日志/后端(和验证 `:core-telemetry-hilt` 是否工作的方式相同)。

**不要做的事**:见"已知限制"——不要把这个桥的逻辑挪回 `:core-net` 或 `:core-telemetry-hilt` 任何一边。

## 公开 API

- `NetworkTelemetryBridgeModule` — `@Module`:
  - `provideNetworkMetricsSink(telemetry: Telemetry): NetworkMetricsSink`
  - `provideWebSocketMetricsSink(telemetry: Telemetry): WebSocketMetricsSink` —— 走 `Telemetry.eventIfEnabled` 逃生口而不是给 `Telemetry` 加强类型方法,原因见 `:core-net` README 的 WebSocket 一节:长连接的生命周期/频率模型和"一次请求一次回调"完全不同,加方法会同时改 `:core-telemetry` 的 golden、`SamplingTelemetry`、`FirebaseTelemetry`、`LogKitTelemetry` 四处。

## 已知限制 / 不要做的事

- **不要**把这个桥的逻辑挪回 `:core-net` 或 `:core-telemetry-hilt` 任何一边——那会强迫只想要其中一半能力的消费方拖上另一半的依赖。
