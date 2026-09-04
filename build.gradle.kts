/**
 * 顶层构建脚本：仅声明插件版本（apply false），供子模块按需 alias 引用。
 */
plugins {
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidLibrary) apply false
    alias(libs.plugins.kotlinAndroid) apply false
    alias(libs.plugins.kotlinJvm) apply false
    alias(libs.plugins.kotlinCompose) apply false
    alias(libs.plugins.kotlinSerialization) apply false
    alias(libs.plugins.kover) apply false
}
