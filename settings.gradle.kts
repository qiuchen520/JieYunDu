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
        // 注：曾为 cloudy（com.github.skydoves:cloudy:1.0.0-alpha01）加入 JitPack 仓库；
        // 该依赖已按 Owner 裁决（方案 A）移除，JitPack 仓库随之移除，减少无用第三方仓库。
    }
}

rootProject.name = "JieYunDu"
include(":app")
