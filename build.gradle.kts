plugins {
    id("net.fabricmc.fabric-loom-remap")
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

val requiredJava = when {
    sc.current.parsed >= "1.20.5" -> JavaVersion.VERSION_21
    sc.current.parsed >= "1.18" -> JavaVersion.VERSION_17
    sc.current.parsed >= "1.17" -> JavaVersion.VERSION_16
    else -> JavaVersion.VERSION_1_8
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
    /**
     * Fetches only the required Fabric API modules to not waste time downloading all of them for each version.
     * @see <a href="https://github.com/FabricMC/fabric">List of Fabric API modules</a>
     */
    fun fapi(vararg modules: String) {
        for (it in modules) modImplementation(fabricApi.module(it, property("deps.fabric_api") as String))
    }

    minecraft("com.mojang:minecraft:${sc.current.version}")
    //mappings(loom.officialMojangMappings())
    mappings("net.fabricmc:yarn:${property("deps.yarn_mappings")}:v2")
    modImplementation("net.fabricmc:fabric-loader:${property("deps.fabric_loader")}")
    compileOnly("org.projectlombok:lombok:${property("deps.lombok")}")
    annotationProcessor("org.projectlombok:lombok:${property("deps.lombok")}")
    testImplementation(platform("org.junit:junit-bom:5.12.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("org.mockito:mockito-core:5.17.0")

    fapi("fabric-lifecycle-events-v1", "fabric-resource-loader-v0", "fabric-content-registries-v0")
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
    targetCompatibility = requiredJava
    sourceCompatibility = requiredJava
}

publishMods {
    file.set(tasks.remapJar.flatMap { it.archiveFile })
    changelog.set(releaseNotes)
    displayName.set("$modName $modVersion (${sc.current.version})")
    type.set(BETA)
    modLoaders.add("fabric")

    modrinth {
        modrinthProjectId?.let(projectId::set)
        modrinthToken?.let(accessToken::set)
        minecraftVersions.add(sc.current.version)
        requires("fabric-api")
    }

    curseforge {
        curseforgeProjectId?.let(projectId::set)
        curseforgeToken?.let(accessToken::set)
        minecraftVersions.add(sc.current.version)
        javaVersions.add(requiredJava)
        requires("fabric-api")
    }
}

tasks {
    withType<org.gradle.api.tasks.testing.Test>().configureEach {
        useJUnitPlatform()
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
        filesMatching("*.mixins.json") { expand("java" to mixinJava) }
    }

    // Builds the version into a shared folder in `build/libs/${mod version}/`
    register<Copy>("buildAndCollect") {
        group = "build"
        from(remapJar.map { it.archiveFile }, remapSourcesJar.map { it.archiveFile })
        into(rootProject.layout.buildDirectory.file("libs/${project.property("mod.version")}"))
        dependsOn("build")
    }
}
