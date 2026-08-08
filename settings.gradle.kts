rootProject.name = "CloudstreamPlugins"

// Dynamic subproject inclusion: Includes any top-level directory containing a build.gradle.kts file
val disabled = listOf<String>()

File(rootDir, ".").eachDir { dir ->
    if (!disabled.contains(dir.name) && File(dir, "build.gradle.kts").exists()) {
        include(dir.name)
    }
}

fun File.eachDir(block: (File) -> Unit) {
    listFiles()?.filter { it.isDirectory }?.forEach(block)
}
