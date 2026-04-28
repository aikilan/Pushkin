plugins {
    id("java")
    kotlin("jvm") version "1.9.25"
    kotlin("plugin.serialization") version "1.9.25"
    id("org.jetbrains.intellij.platform") version "2.1.0"
}

group = "com.commitai"
version = "1.0.0"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        create("IU", "2024.3")
        bundledPlugin("Git4Idea")
        instrumentationTools()
    }

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
}

kotlin {
    jvmToolchain(21)
}

intellijPlatform {
    // 关闭 headless 设置索引生成，避免打包时输出 JetBrains 平台内部 configurable warning。
    buildSearchableOptions.set(false)

    pluginConfiguration {
        ideaVersion {
            sinceBuild = "243"
            untilBuild = "999.*"
        }
    }
}

tasks {
    patchPluginXml {
        version = project.version.toString()
        sinceBuild = "243"
        untilBuild = "999.*"
    }
}
