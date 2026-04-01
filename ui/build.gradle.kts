plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":core"))

    implementation(compose.desktop.currentOs)
    implementation(compose.material3)

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.6.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    testImplementation(kotlin("test"))
}

compose.desktop {
    application {
        mainClass = "com.cortex.localmanager.MainKt"

        nativeDistributions {
            targetFormats(org.jetbrains.compose.desktop.application.dsl.TargetFormat.Exe, org.jetbrains.compose.desktop.application.dsl.TargetFormat.Msi)
            packageName = "CortexLocalManager"
            packageVersion = "1.0.0"
            description = "Cortex XDR Local Emergency Management Tool"
            vendor = "Palo Alto Networks"
            windows {
                menuGroup = "Cortex Local Manager"
                perUserInstall = false
                shortcut = true
                dirChooser = true
                iconFile.set(project.file("src/main/resources/icon.ico"))
            }
            modules("java.naming", "java.sql", "jdk.unsupported")
            jvmArgs("-Xmx512m")
        }
    }
}

tasks.test {
    useJUnitPlatform()
}
