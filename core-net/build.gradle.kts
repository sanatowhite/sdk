plugins {
    alias(libs.plugins.sanato.android.library)
}

android {
    namespace = "io.sanato.apptemplate.core.net"
}

dependencies {
    implementation(project(":core-common"))

    implementation(platform(libs.okhttp.bom))
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.okhttp.mockwebserver)
}
