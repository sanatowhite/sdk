plugins {
    alias(libs.plugins.sanato.android.library)
    alias(libs.plugins.sanato.android.library.published)
    alias(libs.plugins.sanato.api.check)
}

android {
    namespace = "io.sanato.appkit.core.common"

    // 覆盖 convention plugin 的默认 testFixtures.enable=true——这个模块没有
    // src/testFixtures 源码，开着只会让发布产物多出一个空的 -test-fixtures.aar
    // 污染 JitPack 的索引（AGP 在 testFixtures.enable=true 时会自动把对应
    // variant 纳入 maven-publish 的 "release" 软件组件）。
    testFixtures.enable = false
}

dependencies {
    // javax.inject 的 @Qualifier 元注解出现在三个 dispatcher qualifier 注解上——
    // 消费方在自己的注入点写 @IoDispatcher 时，编译器/KSP 处理器要能解析到
    // Qualifier 本身。convention plugin 已经把它声明成 implementation，这里
    // 再声明一次 api 是故意的：同一坐标出现在两处无害，效果以 api 为准。
    api(libs.javax.inject)
}
