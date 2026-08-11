plugins {
    alias(libs.plugins.sanato.android.application)
    alias(libs.plugins.sanato.android.hilt)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.aboutlibraries)
    alias(libs.plugins.baselineprofile)
    // Firebase 是这个模板的默认遥测后端(见 :telemetry-firebase README)——
    // 不再走 telemetryFirebaseEnabled 开关。fork 者换掉 app/google-services.json
    // 指向自己的 Firebase 项目就行;仓库里提交的是一份指向不存在项目的占位
    // json,能让 :app 开箱编译/运行,但真实上报不到任何地方。
    alias(libs.plugins.google.services)
    alias(libs.plugins.firebase.crashlytics)
}

android {
    namespace = "io.sanato.apptemplate"

    buildFeatures {
        compose = true
    }
}

baselineProfile {
    // 生成物提交进版本库，是构建输入不是输出——见 baselineprofile/README 里的说明。
    // 不在每次构建时自动跑一遍 instrumented test（很慢），改为手动/发版触发。
    automaticGenerationDuringBuild = false
}

aboutLibraries {
    // 离线模式:构建期不联网抓 license,CI 不会因为这一步变成不确定的网络依赖。
    offlineMode = true
}

dependencies {
    implementation(project(":core-common"))
    implementation(project(":core-common-hilt"))
    // WebSocket demo 页(ui/websocket/)直接用 WebSocketFactory/HttpClientFactory——
    // 之前只通过 :net-telemetry-hilt 的 api(project(":core-net")) 传递可见,这里
    // 显式加一条直接依赖,因为 :app 现在是真的在用它的类型,不只是传递可见。
    implementation(project(":core-net"))
    implementation(project(":core-init"))
    implementation(project(":core-init-hilt"))
    implementation(project(":core-ui"))
    implementation(project(":core-data"))
    implementation(project(":core-data-hilt"))
    implementation(project(":core-telemetry"))
    implementation(project(":core-telemetry-hilt"))
    implementation(project(":net-telemetry-hilt"))
    implementation(project(":updatechecker"))
    // 加密滚动日志 SDK——见 logkit/README.md。零内部依赖，:app 是唯一的接线方。
    implementation(project(":logkit"))
    // 默认遥测后端——见 app/build.gradle.kts 顶部 plugins{} 块的说明。
    implementation(project(":telemetry-firebase"))
    // 设置/关于/隐私政策/用户协议/同意/What's New——见 feature-settings/README.md。
    implementation(project(":feature-settings"))
    // 反馈页(邮件形式,附带截图/日志)——见 feature-feedback/README.md。
    implementation(project(":feature-feedback"))
    // 开源许可页——依赖下面 aboutLibraries{} 生成的 R.raw.aboutlibraries,
    // 见 feature-licenses/README.md。
    implementation(project(":feature-licenses"))
    // 更新检查对话框 + 状态持有——见 feature-update/README.md。
    implementation(project(":feature-update"))
    // Firebase Auth 托管登录(邮箱+密码/Google/Apple/手机验证码)——见
    // core-auth/auth-firebase/auth-net-hilt/feature-auth 各自 README.md。
    implementation(project(":core-auth"))
    implementation(project(":auth-firebase"))
    implementation(project(":auth-net-hilt"))
    implementation(project(":feature-auth"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.datastore.preferences)

    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)

    debugImplementation(project(":debug-tools"))

    implementation(libs.androidx.profileinstaller)
    baselineProfile(project(":baselineprofile"))

    testImplementation(libs.junit4)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.mockk)
    testImplementation(testFixtures(project(":core-data")))
}
