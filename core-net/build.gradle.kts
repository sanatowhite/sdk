plugins {
    alias(libs.plugins.sanato.android.library)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.sanato.android.library.published)
    alias(libs.plugins.sanato.api.check)
}

android {
    namespace = "io.sanato.appkit.core.net"

    defaultConfig {
        consumerProguardFiles("consumer-rules.pro")
    }

    // 见 core-common/build.gradle.kts 的同一条注释：这个模块没有 src/testFixtures
    // 源码，关掉避免发布出一个空的 -test-fixtures.aar。
    testFixtures.enable = false
}

dependencies {
    // AppResult<T> 是 safeApiCall 的返回类型。
    api(project(":core-common"))

    // OkHttpClient（HttpClientFactory.okHttpClient() 的返回类型）、Interceptor
    // （additionalInterceptors 的元素类型 + RetryInterceptor 的超接口）——两者
    // 都直接出现在公开签名里。
    api(platform(libs.okhttp.bom))
    api(libs.okhttp)
    // Retrofit（HttpClientFactory.retrofit() 的返回类型）。
    api(libs.retrofit)
    // Json（defaultJson 的类型 + retrofit(json: Json) 的参数类型）。
    api(libs.kotlinx.serialization.json)

    // 只在 okHttpClient()/retrofit() 函数体内部使用，不出现在任何公开签名，
    // 保持 implementation：
    //   okhttp-logging —— HttpLoggingInterceptor 只在函数体内构造。
    //   retrofit-kotlinx-serialization —— asConverterFactory 只在函数体内调用。
    implementation(libs.okhttp.logging)
    implementation(libs.retrofit.kotlinx.serialization)

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
