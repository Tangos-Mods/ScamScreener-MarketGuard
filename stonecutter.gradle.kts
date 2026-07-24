plugins {
    id("dev.kikugie.stonecutter")
    id("net.fabricmc.fabric-loom") version "1.15.5" apply false
    id("me.modmuss50.mod-publish-plugin") version "1.0.0" apply false
}

stonecutter active "26.2"

// Make newer versions be published last
stonecutter tasks {
    order("publishModrinth")
    order("publishCurseforge")
}

tasks.register("publishModrinthAll") {
    group = "publishing"
    description = "Publishes every configured Stonecutter version to Modrinth."
    dependsOn(stonecutter.tasks.named("publishModrinth").map { it.values })
}

tasks.register("publishCurseforgeAll") {
    group = "publishing"
    description = "Publishes every configured Stonecutter version to CurseForge."
    dependsOn(stonecutter.tasks.named("publishCurseforge").map { it.values })
}

tasks.register("publishAllUploads") {
    group = "publishing"
    description = "Publishes every configured Stonecutter version to Modrinth and CurseForge."
    dependsOn("publishModrinthAll", "publishCurseforgeAll")
}

tasks.register("buildDevJars") {
    group = "build"
    description = "Builds local-API development JARs for every configured Stonecutter version."
    dependsOn(stonecutter.tasks.named("devJar").map { it.values })
}

// See https://stonecutter.kikugie.dev/wiki/config/params
stonecutter parameters {
    swaps["mod_version"] = "\"${property("mod.version")}\";"
    swaps["minecraft"] = "\"${node.metadata.version}\";"
    constants["release"] = true
    dependencies["fapi"] = node.project.property("deps.fabric_api") as String

}
