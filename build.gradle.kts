plugins {
    id("com.android.application") version "9.2.0" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.3.10" apply false
}

System.getenv("TEXTORY_BUILD_ROOT")?.let { root ->
    layout.buildDirectory.set(file("$root/root"))
    subprojects {
        layout.buildDirectory.set(file("$root/${project.name}"))
    }
}
