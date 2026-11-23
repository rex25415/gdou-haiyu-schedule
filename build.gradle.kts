plugins {
    id("com.android.application") version "8.10.1" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.0.21" apply false
}

// Keep build artifacts off C: drive when customBuildRoot property is supplied
val customBuildRoot = providers.gradleProperty("customBuildRoot").orNull
if (!customBuildRoot.isNullOrBlank()) {
    allprojects {
        layout.buildDirectory.set(file("$customBuildRoot/${project.name}"))
    }
}
