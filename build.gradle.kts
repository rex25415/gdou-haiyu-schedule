plugins {
    id("com.android.application") version "9.5.2" apply false
    id("org.jetbrains.kotlin.android") version "2.4.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.21" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.4.21" apply false
}

// Keep build artifacts off C: drive when customBuildRoot property is supplied
val customBuildRoot = providers.gradleProperty("customBuildRoot").orNull
if (!customBuildRoot.isNullOrBlank()) {
    allprojects {
        layout.buildDirectory.set(file("$customBuildRoot/${project.name}"))
    }
}
