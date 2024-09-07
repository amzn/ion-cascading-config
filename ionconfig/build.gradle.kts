plugins {
    java
    id("io.freefair.lombok") version "8.4"
    `maven-publish`
}

group = "com.amazon"
version = "1.2"

repositories {
    mavenCentral()
}

sourceSets.main.get().java.srcDir("src")
sourceSets.test.get().java.srcDir("tst")

dependencies {
    implementation("com.amazon.ion:ion-java:1.9.5")
    implementation("com.fasterxml.jackson.core:jackson-core:2.14.2")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-ion:2.14.2")
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.9.2")
    testImplementation("org.junit.jupiter:junit-jupiter-params:5.9.2")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.9.2")
}

tasks.getByName<Test>("test") {
    useJUnitPlatform()
}

tasks {
    withType<JavaCompile> {
        // Because we set the `release` option, you can no longer build ion-java using JDK 8. However, we continue to
        // emit JDK 8 compatible classes due to widespread use of this library with JDK 8.
        options.release.set(8)
    }
}