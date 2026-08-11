plugins {
    alias(libs.plugins.sanato.android.library)
    alias(libs.plugins.sanato.android.hilt)
    alias(libs.plugins.sanato.android.library.published)
    alias(libs.plugins.sanato.api.check)
}

android {
    namespace = "io.sanato.appkit.auth.net.hilt"
    testFixtures.enable = false
}

dependencies {
    // AuthInterceptor/AuthTokenAuthenticator take an AuthTokenProvider constructor
    // param — a consumer type, must be api.
    api(project(":core-auth"))
    // AuthInterceptor : okhttp3.Interceptor, AuthTokenAuthenticator : okhttp3.Authenticator,
    // @Authenticated OkHttpClient — all three OkHttp types are visible here only
    // because :core-net's api() already put them on the classpath, so this is
    // api(project(":core-net")) rather than re-declaring the okhttp coordinate.
    api(project(":core-net"))
    // isDebuggableBuild() extension used when assembling the authenticated client.
    implementation(project(":core-common"))

    implementation(libs.hilt.android)

    testImplementation(libs.okhttp.mockwebserver)
}
