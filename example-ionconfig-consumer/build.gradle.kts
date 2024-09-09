plugins {
    java
}

group = "com.amazon.ionconfig.example"
version = "1.0"

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":example-ionconfig-consumer"))
    implementation(files("../ionconfig/build/libs/ion-cascading-config-1.2.jar"))
}

tasks {
    register<JavaExec>("run") {
        dependsOn("build")
        classpath = project.sourceSets.main.get().runtimeClasspath
        mainClass.set("com.amazon.ionconfig.example.Main")
    }
}

