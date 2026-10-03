// 文件：settings.gradle.kts
// 职责：Gradle 工程定义，声明插件仓库、依赖仓库与包含模块
// 依赖：gradle/libs.versions.toml
// 协议：AGPL-3.0

pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // Cloudy 发布于 JitPack（§5 指定依赖 com.github.skydoves:cloudy:1.0.0-alpha01），
        // 缺少此仓库会导致依赖解析失败。
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "JieYunDu"
include(":app")
