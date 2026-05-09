import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.composeHotReload)
}

kotlin {
    jvm()

    sourceSets {
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
        jvmMain.dependencies {
            implementation("org.xerial:sqlite-jdbc:3.45.1.0")
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinx.coroutinesSwing)
            implementation(compose.components.resources)
            implementation("org.jetbrains.compose.components:components-resources:1.6.1") // для ресурсов
            implementation("org.jetbrains.compose.material:material-icons-extended:1.6.1") // расширенные иконки

        }
    }
}


compose.desktop {
    application {
        mainClass = "tim.projekt.bsw.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb ,TargetFormat.Exe)
            packageName = "tim.projekt.bsw"
            packageVersion = "1.0.0"
            description = "Instant file search BSW"
            copyright = "© 2026 timaborovikov"
            windows {
                shortcut = true
                menu = true
                iconFile.set(project.file("src/jvmMain/resources/icon.ico"))
            }
        }
    }
}


