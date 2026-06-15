import java.net.URI
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

plugins {
    java
    application
    id("com.gradleup.shadow") version "9.2.2"
}

group = "dev.johns"
version = "0.1.0"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.codemc.io/repository/maven-releases/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.10-R0.1-SNAPSHOT")
    implementation("com.github.retrooper:packetevents-spigot:2.12.2")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.18.2")

    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(21)
}

application {
    mainClass = "dev.johns.breaktelemetry.analysis.CompareMain"
}

tasks.test {
    useJUnitPlatform()
}

tasks.processResources {
    filesMatching("plugin.yml") {
        expand("version" to project.version)
    }
}

tasks.shadowJar {
    archiveClassifier = ""
    relocate("com.github.retrooper.packetevents", "dev.johns.breaktelemetry.lib.packetevents")
    relocate("io.github.retrooper.packetevents", "dev.johns.breaktelemetry.lib.packeteventsapi")
    relocate("com.fasterxml.jackson", "dev.johns.breaktelemetry.lib.jackson")
    mergeServiceFiles()
}

tasks.jar { enabled = false }
tasks.assemble { dependsOn(tasks.shadowJar) }

val serverDir = layout.projectDirectory.dir("run")
val paperJar = serverDir.file("paper-1.21.10.jar")

val downloadPaper by tasks.registering {
    outputs.file(paperJar)
    doLast {
        val destination = paperJar.asFile
        if (!destination.exists()) {
            destination.parentFile.mkdirs()
            val api = URI("https://fill.papermc.io/v3/projects/paper/versions/1.21.10/builds").toURL()
            val builds = (groovy.json.JsonSlurper().parse(api) as List<*>)
                .filterIsInstance<Map<*, *>>()
                .filter { it["channel"] == "STABLE" }
            val build = builds.maxByOrNull { (it["id"] as Number).toInt() }
                ?: error("Paper has no stable build for Minecraft 1.21.10")
            val download = ((build["downloads"] as Map<*, *>)["server:default"] as Map<*, *>)
            val expectedSha256 = ((download["checksums"] as Map<*, *>)["sha256"] as String)
            URI(download["url"] as String).toURL().openStream().use {
                Files.copy(it, destination.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
            val actualSha256 = MessageDigest.getInstance("SHA-256")
                .digest(Files.readAllBytes(destination.toPath()))
                .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
            check(actualSha256 == expectedSha256) {
                destination.delete()
                "Paper download checksum mismatch: expected $expectedSha256, got $actualSha256"
            }
        }
    }
}

val prepareServer by tasks.registering {
    dependsOn(downloadPaper, tasks.shadowJar)
    doLast {
        val dir = serverDir.asFile
        dir.mkdirs()
        dir.resolve("plugins").mkdirs()
        dir.resolve("eula.txt").writeText("eula=true\n")
        dir.resolve("server.properties").writeText(
            """
            # WSL and the Windows Minecraft client use different loopback interfaces.
            # Binding all WSL interfaces allows Windows localhost forwarding to work.
            server-ip=0.0.0.0
            server-port=25565
            online-mode=true
            white-list=true
            enforce-whitelist=true
            gamemode=survival
            difficulty=normal
            spawn-protection=0
            enable-command-block=false
            view-distance=8
            simulation-distance=8
            motd=Local Break Telemetry Lab
            """.trimIndent() + "\n"
        )
        copy {
            from(tasks.shadowJar.flatMap { it.archiveFile })
            into(dir.resolve("plugins"))
            rename { "BreakTelemetry.jar" }
        }
    }
}

tasks.register<JavaExec>("runServer") {
    dependsOn(prepareServer)
    workingDir(serverDir)
    classpath = files(paperJar)
    mainClass = "io.papermc.paperclip.Main"
    jvmArgs("-Xms1G", "-Xmx2G")
    standardInput = System.`in`
}

tasks.register<JavaExec>("compareCaptures") {
    dependsOn(tasks.classes)
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass = application.mainClass
}

tasks.register<JavaExec>("inspectCapture") {
    dependsOn(tasks.classes)
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass = "dev.johns.breaktelemetry.analysis.InspectMain"
}
