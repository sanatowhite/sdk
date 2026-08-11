plugins {
    alias(libs.plugins.sanato.android.library.compose)
    alias(libs.plugins.sanato.android.hilt)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.sanato.android.library.published)
    // 刻意不 apply sanato.api.check：和 core-ui/feature-settings/feature-update
    // 同样的理由——这个模块的公开签名里 SignInScreen/PhoneCodeScreen/... 全是
    // @Composable 函数，Compose 编译器注入的 Composer/$$changed 参数会随编译器
    // 版本整体漂移。API 稳定性改由 consumer-smoke 兜底。
}

android {
    namespace = "io.sanato.appkit.feature.auth"

    // 见 feature-settings/build.gradle.kts 同一条注释。
    resourcePrefix = "appkit_"

    // 没有 src/testFixtures 源码，关掉避免发布出一个空的 -test-fixtures.aar。
    testFixtures.enable = false
}

dependencies {
    // AuthUser/AuthState/AuthError/AuthRepository/AuthProvider 直接出现在
    // Route/ViewModel 的公开签名里。
    api(project(":core-auth"))
    api(project(":core-ui"))
    // NavGraphBuilder/NavController 出现在 authGraph() 的公开签名里。
    api(libs.androidx.navigation.compose)

    // AppResult 只在 ViewModel 内部 when 分支里用，不出现在公开签名。
    implementation(project(":core-common"))
    implementation(libs.hilt.navigation.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit4)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.mockk)
    testImplementation(testFixtures(project(":core-auth")))

    // PhoneCodeViewModelTest needs SavedStateHandle.toRoute<T>() to actually decode nav args,
    // which touches real android.os.Bundle internals even in a unit test — AGP's default stub
    // jar throws "not mocked" for that, hence Robolectric (same reasoning as :auth-firebase's
    // error-mapping tests needing it for real FirebaseAuthException construction).
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)
}
