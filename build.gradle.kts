plugins {
    id("net.fabricmc.fabric-loom")
    id("me.modmuss50.mod-publish-plugin")
}

version = "${property("mod.version")}+${sc.current.version}"
base.archivesName = property("mod.id") as String

fun File.readDotEnv(): Map<String, String> {
    if (!isFile) return emptyMap()

    return readLines()
        .asSequence()
        .map(String::trim)
        .filter { it.isNotEmpty() && !it.startsWith("#") }
        .mapNotNull { line ->
            val separator = line.indexOf('=')
            if (separator <= 0) return@mapNotNull null

            val key = line.substring(0, separator).trim()
            val value = line.substring(separator + 1).trim()
                .removeSurrounding("\"")
                .removeSurrounding("'")

            key to value
        }
        .associate { it }
}

fun configuredProperty(name: String): String? =
    providers.gradleProperty(name).orNull
        ?.trim()
        ?.takeUnless { it.isEmpty() || it.startsWith("#") }

val dotenv = rootProject.file(".env").readDotEnv()
val modVersion = property("mod.version") as String
val modName = property("mod.name") as String
val modType = configuredProperty("mod.type")
    ?: throw GradleException("Missing mod.type. Expected one of: ALPHA, BETA, STABLE.")
val modrinthProjectId = configuredProperty("publish.modrinth")
val curseforgeProjectId = configuredProperty("publish.curseforge")
val modrinthToken = System.getenv("MODRINTH_TOKEN")?.trim()?.takeIf { it.isNotEmpty() }
    ?: dotenv["MODRINTH_TOKEN"]?.trim()?.takeIf { it.isNotEmpty() }
val curseforgeToken = System.getenv("CURSEFORGE_TOKEN")?.trim()?.takeIf { it.isNotEmpty() }
    ?: dotenv["CURSEFORGE_TOKEN"]?.trim()?.takeIf { it.isNotEmpty() }
val releaseNotes = rootProject.file("CHANGELOG.md")
    .takeIf(File::isFile)
    ?.readText()
    ?.trim()
    ?.takeIf(String::isNotEmpty)
    ?: "$modName $modVersion"

val requiredJava = JavaVersion.VERSION_25
val minecraftTitle = project.property("mod.mc_title") as String
val targetMinecraftVersions = (project.property("mod.mc_targets") as String)
    .split(Regex("\\s+"))
    .filter(String::isNotBlank)

val releaseType = try {
    me.modmuss50.mpp.ReleaseType.of(modType.uppercase())
} catch (_: IllegalArgumentException) {
    throw GradleException("Invalid mod.type '$modType'. Expected one of: ALPHA, BETA, STABLE.")
}

repositories {
    /**
     * Restricts dependency search of the given [groups] to the [maven URL][url],
     * improving the setup speed.
     */
    fun strictMaven(url: String, alias: String, vararg groups: String) = exclusiveContent {
        forRepository { maven(url) { name = alias } }
        filter { groups.forEach(::includeGroup) }
    }
    strictMaven("https://www.cursemaven.com", "CurseForge", "curse.maven")
    strictMaven("https://api.modrinth.com/maven", "Modrinth", "maven.modrinth")
}

dependencies {
    val scamscreenerVersion = "2.2.0+26.1"

    minecraft("com.mojang:minecraft:${sc.current.version}")
    implementation("net.fabricmc:fabric-loader:${property("deps.fabric_loader")}")
    implementation("net.fabricmc.fabric-api:fabric-api:${property("deps.fabric_api")}")
    compileOnly("org.projectlombok:lombok:${property("deps.lombok")}")
    annotationProcessor("org.projectlombok:lombok:${property("deps.lombok")}")
    testImplementation(platform("org.junit:junit-bom:5.12.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("org.mockito:mockito-core:5.17.0")
    compileOnly("maven.modrinth:scamscreener:$scamscreenerVersion")
}

loom {
    fabricModJsonPath = rootProject.file("src/main/resources/fabric.mod.json") // Useful for interface injection
    accessWidenerPath = rootProject.file("src/main/resources/marketguard.accesswidener")

    decompilerOptions.named("vineflower") {
        options.put("mark-corresponding-synthetics", "1") // Adds names to lambdas - useful for mixins
    }

    runConfigs.all {
        ideConfigGenerated(true)
        vmArgs("-Dmixin.debug.export=true") // Exports transformed classes for debugging
        runDir = "../../run" // Shares the run directory between versions
    }
}

java {
    withSourcesJar()
    toolchain.languageVersion = JavaLanguageVersion.of(25)
    targetCompatibility = requiredJava
    sourceCompatibility = requiredJava
}

publishMods {
    file.set(project.tasks.named("jar", org.gradle.jvm.tasks.Jar::class.java).flatMap { it.archiveFile })
    changelog.set(releaseNotes)
    displayName.set("$modName $modVersion ($minecraftTitle)")
    type.set(releaseType)
    modLoaders.add("fabric")

    modrinth {
        modrinthProjectId?.let(projectId::set)
        modrinthToken?.let(accessToken::set)
        minecraftVersions.addAll(targetMinecraftVersions)
        requires("fabric-api")
    }

    curseforge {
        curseforgeProjectId?.let(projectId::set)
        curseforgeToken?.let(accessToken::set)
        minecraftVersions.addAll(targetMinecraftVersions)
        javaVersions.add(requiredJava)
        requires("fabric-api")
    }
}

tasks {
    withType<org.gradle.api.tasks.testing.Test>().configureEach {
        useJUnitPlatform()
        jvmArgs("-Dnet.bytebuddy.experimental=true", "-XX:+EnableDynamicAgentLoading")
        testLogging {
            events(
                org.gradle.api.tasks.testing.logging.TestLogEvent.PASSED,
                org.gradle.api.tasks.testing.logging.TestLogEvent.SKIPPED,
                org.gradle.api.tasks.testing.logging.TestLogEvent.FAILED
            )
        }
    }

    processResources {
        inputs.property("id", project.property("mod.id"))
        inputs.property("name", project.property("mod.name"))
        inputs.property("version", project.property("mod.version"))
        inputs.property("minecraft", project.property("mod.mc_dep"))

        val props = mapOf(
            "id" to project.property("mod.id"),
            "name" to project.property("mod.name"),
            "version" to project.property("mod.version"),
            "minecraft" to project.property("mod.mc_dep")
        )

        filesMatching("fabric.mod.json") { expand(props) }

        val mixinJava = "JAVA_${requiredJava.majorVersion}"
        filesMatching("mixins.marketguard.json") { expand("java" to mixinJava) }
    }

    // Builds the version into a shared folder in `build/libs/${mod version}/`
    register<Copy>("buildAndCollect") {
        group = "build"
        from(
            project.tasks.named("jar", org.gradle.jvm.tasks.Jar::class.java).flatMap { it.archiveFile },
            project.tasks.named("sourcesJar", org.gradle.jvm.tasks.Jar::class.java).flatMap { it.archiveFile }
        )
        into(rootProject.layout.buildDirectory.file("libs/${project.property("mod.version")}"))
        dependsOn("build")
    }
}
