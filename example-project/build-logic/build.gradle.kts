plugins {
    `java-gradle-plugin`
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("dev.workfunctions:gradle-work-functions:0.1.0")
}

gradlePlugin {
    plugins {
        create("version") {
            id = "example.version"
            implementationClass = "example.buildlogic.VersionPlugin"
        }
    }
}
