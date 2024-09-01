plugins {
    id("com.hayden.apt")
    id("com.hayden.spring")
    id("com.hayden.no-main-class")
    id("com.hayden.aop")
}

tasks.register("prepareKotlinBuildScriptModel") {}

group = "com.hayden"
version = "1.0.0"

