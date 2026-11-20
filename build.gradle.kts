plugins {
    `java-gradle-plugin`
    `kotlin-dsl`
    groovy
}

group = "dev.workfunctions"
version = "0.1.0"

repositories {
    mavenCentral()
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

gradlePlugin {
    plugins {
        create("workFunctions") {
            id = "dev.workfunctions"
            implementationClass = "dev.workfunctions.WorkFunctionsPlugin"
        }
    }
}

testing {
    suites {
        named<JvmTestSuite>("test") {
            useSpock("2.3-groovy-4.0")
        }
    }
}

tasks.test {
    // Functional tests share one TestKit directory, so their daemons and caches are reused.
    systemProperty("testKitDir", layout.buildDirectory.dir("testkit").get().asFile.absolutePath)
}
