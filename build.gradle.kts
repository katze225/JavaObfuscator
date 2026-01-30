plugins {
    id("java")
}

group = "me.katze225"
version = "1.0-SNAPSHOT"

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.ow2.asm:asm:9.6")
    implementation("org.ow2.asm:asm-tree:9.6")
    implementation("org.ow2.asm:asm-commons:9.6")
    implementation("org.ow2.asm:asm-util:9.6")

    compileOnly("org.projectlombok:lombok:1.18.42")
    annotationProcessor("org.projectlombok:lombok:1.18.42")
}

tasks.register("cleanAppleDouble") {
    doLast {
        delete(fileTree(projectDir) {
            include("***/._*")
        })
        if (buildDir.exists()) {
            delete(fileTree(buildDir) {
                include("***/._*")
            })
        }
        println("Удалены все Apple Double файлы (._*)")
    }
}

tasks.named("clean") {
    dependsOn("cleanAppleDouble")
}

tasks.compileJava {
    dependsOn("cleanAppleDouble")
}

tasks.jar {
    dependsOn("cleanAppleDouble")
    manifest {
        attributes(
            "Main-Class" to "me.katze225.Main"
        )
    }
    from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

tasks.test {
    useJUnitPlatform()
}