plugins {
    alias(libs.plugins.sanato.android.library)
    alias(libs.plugins.sanato.android.hilt)
    alias(libs.plugins.sanato.android.library.published)
    alias(libs.plugins.sanato.api.check)
}

android {
    namespace = "io.sanato.appkit.net.telemetry.hilt"

    // 没有 src/testFixtures 源码，关掉避免发布出一个空的 -test-fixtures.aar。
    testFixtures.enable = false
}

dependencies {
    // 唯一的跨 Tier-1 粘合桥：core-net 的 NetworkMetricsSink 接口 → 转发到
    // core-telemetry 的 Telemetry.networkRequest。core-net 与 core-telemetry
    // 之间保持零依赖边，粘合只发生在这个独立模块——只有同时引了两者的消费方
    // 才需要它，避免"只想要网络栈"或"只想要遥测"的消费方被迫拖上另一半。
    api(project(":core-net"))
    api(project(":core-telemetry"))
}
