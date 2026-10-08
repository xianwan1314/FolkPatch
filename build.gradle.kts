import java.net.URI

plugins {
    alias(libs.plugins.agp.app) apply false
    alias(libs.plugins.agp.lib) apply false
    alias(libs.plugins.kotlin) apply false
    alias(libs.plugins.kotlin.compose.compiler) apply false
}

val managerVersionBrand = "vivo"

project.ext.set("kernelPatchRepoOwner", "xianwan1314")
project.ext.set("kernelPatchRepoName", "KernelPatch-FolkPatch")
project.ext.set("managerVersionBrand", managerVersionBrand)

fun fetchLatestGitHubReleaseTag(owner: String, repo: String): String? {
    val apiUrl = "https://api.github.com/repos/$owner/$repo/releases/latest"
    return runCatching {
        val connection = URI.create(apiUrl).toURL().openConnection().apply {
            connectTimeout = 5000
            readTimeout = 5000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            setRequestProperty("User-Agent", "FolkPatch-Gradle")
        }
        connection.getInputStream().bufferedReader().use { it.readText() }
    }.getOrNull()?.let { response ->
        Regex(""""tag_name"\s*:\s*"([^"]+)"""")
            .find(response)
            ?.groupValues
            ?.getOrNull(1)
            ?.removePrefix("v")
    }
}

fun resolveKernelPatchVersion(owner: String, repo: String): String {
    val fallbackVersion = "0.13.4"
    val overriddenVersion = sequenceOf(
        providers.gradleProperty("kernelPatchVersion").orNull,
        System.getenv("KERNELPATCH_VERSION"),
    ).mapNotNull { it?.trim() }.firstOrNull { it.isNotEmpty() }
    if (overriddenVersion != null) {
        println("Using overridden KernelPatch version: $overriddenVersion")
        return overriddenVersion.removePrefix("v")
    }

    val latestVersion = fetchLatestGitHubReleaseTag(owner, repo)
    if (latestVersion != null) {
        println("Using latest KernelPatch release: $latestVersion")
        return latestVersion
    }

    println("Failed to resolve latest KernelPatch release, fallback to $fallbackVersion")
    return fallbackVersion
}

project.ext.set(
    "kernelPatchVersion",
    resolveKernelPatchVersion(
        project.ext.get("kernelPatchRepoOwner") as String,
        project.ext.get("kernelPatchRepoName") as String,
    )
)

val androidMinSdkVersion by extra(26)
val androidTargetSdkVersion by extra(36)
val androidCompileSdkVersion by extra(37)
val androidBuildToolsVersion by extra("36.1.0")
val androidCompileNdkVersion by extra("30.0.15729638")
val androidSourceCompatibility by extra(JavaVersion.VERSION_21)
val androidTargetCompatibility by extra(JavaVersion.VERSION_21)
val managerVersionBaseName by extra(getBaseVersionName())
val managerVersionCode by extra(getVersionCode())
val managerVersionName by extra(getVersionName())
val branchName by extra(getbranch())
fun Project.exec(command: String, default: String): String {
    return try {
        providers.exec {
            commandLine(command.split(" "))
            isIgnoreExitValue = true
        }.standardOutput.asText.get().trim().takeIf { it.isNotEmpty() } ?: default
    } catch (e: Exception) {
        default
    }
}

fun getVersionProperties(): java.util.Properties {
    val properties = java.util.Properties()
    File(rootDir, "version.properties").inputStream().use(properties::load)
    return properties
}

fun getVersionProperty(name: String): String {
    return getVersionProperties().getProperty(name)
        ?: error("$name not found in version.properties")
}

fun getGitCommitCount(): Int {
    val count = exec("git rev-list --count HEAD", "")
    return count.toIntOrNull()?.takeIf { it > 0 }
        ?: error("Failed to determine git commit count; GitHub checkout must use fetch-depth: 0")
}

fun getVersionCode(): Int {
    val code = getVersionProperty("managerVersionEpoch").toInt() + getGitCommitCount()
    val floor = getVersionProperty("managerVersionFloor").toInt()
    require(code > floor) {
        "Computed versionCode $code is not greater than managerVersionFloor=$floor"
    }
    return code
}

fun getbranch(): String {
    return exec("git rev-parse --abbrev-ref HEAD", "unknown")
}

fun getVersionName(): String {
    val name = exec("git rev-parse --verify --short HEAD", "")
    return name.takeIf { it.isNotEmpty() }
        ?: error("Failed to determine git commit for versionName")
}

fun getVersionName(): String {
    return "${getBaseVersionName()}-$managerVersionBrand"
}

tasks.register("printVersion") {
    doLast {
        println("Version code: $managerVersionCode")
        println("Version name: $managerVersionName")
        println("Commit count: ${getGitCommitCount()}")
        println("Version floor: ${getVersionProperty("managerVersionFloor")}")
    }
}
