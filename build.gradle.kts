// 文件：build.gradle.kts
// 职责：根工程构建脚本，只声明插件别名（apply false），模块配置全部放在 app 模块
// 依赖：gradle/libs.versions.toml
// 协议：AGPL-3.0

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
}
