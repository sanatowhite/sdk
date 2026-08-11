// google-services / firebase-crashlytics 插件不在这里 apply——它们要求
// applicationId 和 google-services.json 的 package_name 匹配,只能挂在 :app。
// 这个模块只提供 Hilt 绑定 + Firebase SDK 依赖。firebase-auth 作为普通 Maven
// 依赖能正常编译(:telemetry-firebase 已证明这一点)——consumer-smoke 工程没有
// google-services 插件也没有 google-services.json,只在运行时 FirebaseApp.
// initializeApp 找不到 options 才会失败,而 smoke 只跑 assembleDebug 从不启动。
// 这也是为什么 FirebaseAuthModule 的 @Provides 内部绝不能在构造期就调
// FirebaseAuth.getInstance()——必须推迟到第一次真正登录。
plugins {
    alias(libs.plugins.sanato.android.library)
    // 必须 apply——spike-0000 证实库模块的 @Module 若不自己跑 hilt-compiler(KSP),
    // @AggregatedDeps 元数据不会生成,:app 聚合不到,且不报任何编译错误。
    alias(libs.plugins.sanato.android.hilt)
    alias(libs.plugins.sanato.android.library.published)
    alias(libs.plugins.sanato.api.check)
}

android {
    namespace = "io.sanato.appkit.auth.firebase"
    testFixtures.enable = false
}

dependencies {
    // FirebaseAuthRepository : AuthRepository/AuthTokenProvider(超接口)——
    // AuthUser/AuthState/AuthError 直接出现在每个公开方法的签名里,必须 api。
    api(project(":core-auth"))

    implementation(libs.hilt.android)

    // ── ADR 0011 条件 3:vendor 依赖全部 implementation,一个 api 都没有。 ──
    // 公开签名里不出现任何 com.google.firebase.* / com.google.android.* 类型,
    // 唯一转换点是 internal fun FirebaseUser.toAuthUser(): AuthUser。
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    // FirebaseAuth 全套是 Task<T> 回调 API,这里换成 await()。
    implementation(libs.kotlinx.coroutines.play.services)
    // Google 登录:Credential Manager 链条,只在方法体内使用,拿到的东西只是
    // 一个 String idToken——见 README"为什么可以带三方依赖"一节。
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.googleid)

    // Constructing real Firebase/GMS exception types (FirebaseAuthException,
    // FirebaseNetworkException, ...) touches android.text.TextUtils internally
    // even just to build the object — AGP's default unit-test stub jar throws
    // "not mocked" for that, so the error-mapping tests need Robolectric's
    // shadows, unlike the rest of this repo's plain-JVM unit tests.
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)
}
