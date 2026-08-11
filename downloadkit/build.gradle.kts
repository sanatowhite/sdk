plugins {
    alias(libs.plugins.sanato.android.library)
    // TaskMetadata (store/TaskMetadata.kt) is @Serializable — the compiler
    // plugin has to be applied in *this* module, :core-net applying it for
    // its own types doesn't help types declared here.
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.sanato.android.library.published)
    alias(libs.plugins.sanato.api.check)
}

android {
    namespace = "io.sanato.appkit.download"
    // 没有 src/testFixtures 源码，关掉避免发布出一个空的 -test-fixtures.aar
    // (同 :core-net 的理由)。
    testFixtures.enable = false

    defaultConfig {
        consumerProguardFiles("consumer-rules.pro")
    }

    // AndroidDownloadNotifier reads string resources (notification texts) —
    // Robolectric needs the real resource table loaded to resolve them,
    // same as :backupkit / :backupkit-drive's tests.
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
    }
}

dependencies {
    // OkHttpClient 出现在 Downloader 的公开构造参数和 Downloader.downloadOkHttpClient()
    // 工厂方法的签名里——必须 api，同 :auth-net-hilt 的 @Authenticated OkHttpClient
    // 绑定是同一条理由(ADR 0009)。这也是本仓第一个依赖 :core-net 而不是零依赖自包含的
    // *kit 模块，见 docs/adr/0013 记录的取舍。
    //
    // kotlinx-serialization-json 供 TaskStore 内部的 .meta JSON 编解码使用——不用
    // 额外声明，:core-net 已经把它 api() 出来了，随 api(project(":core-net")) 传递可用。
    api(project(":core-net"))

    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)
}
