plugins {
    alias(libs.plugins.sanato.android.library)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "io.sanato.apptemplate.core.net"

    defaultConfig {
        consumerProguardFiles("consumer-rules.pro")
    }
}

dependencies {
    implementation(project(":core-common"))

    implementation(platform(libs.okhttp.bom))
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.kotlinx.serialization.json)

    // Retrofit 3.0.0 的 POM 传递声明的是 OkHttp 4.12,但官方确认 4/5 二进制兼容
    // (square/retrofit#4558)。okhttp-bom 这个 platform() 依赖已经能把它对齐到
    // 5.4.0,这里再显式声明一条 constraint 只是为了把"为什么"记录在案。
    constraints {
        implementation("com.squareup.okhttp3:okhttp:${libs.versions.okhttp.get()}") {
            because("Retrofit 3.0.0 ships OkHttp 4.12; OkHttp 4/5 binary compatible (square/retrofit#4558)")
        }
    }

    testImplementation(libs.okhttp.mockwebserver)
}
