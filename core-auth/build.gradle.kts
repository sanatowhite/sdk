plugins {
    alias(libs.plugins.sanato.android.library)
    alias(libs.plugins.sanato.android.library.published)
    alias(libs.plugins.sanato.api.check)
}

android {
    namespace = "io.sanato.appkit.core.auth"
}

dependencies {
    // AppResult<T> is the return type of every AuthRepository method.
    api(project(":core-common"))

    // StateFlow<AuthState>/Flow<PhoneAuthEvent> appear in AuthRepository's public
    // signatures. sanato.android.library only gives implementation, must promote
    // explicitly — same reasoning as :core-data's coroutines dependency.
    api(libs.kotlinx.coroutines.android)

    testFixturesImplementation(libs.kotlinx.coroutines.android)
}
