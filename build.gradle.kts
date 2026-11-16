// Root build file. Plugin versions live in gradle/libs.versions.toml.
//
// AGP 9.x compiles Kotlin itself ("built-in Kotlin"), so `org.jetbrains.kotlin.android` must NOT be applied.
// The Compose compiler Gradle plugin is still required, and its version must match the Kotlin version AGP uses.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.room) apply false
}
