android {
    namespace = "com.thealyss.cloudstream.animasu"
}

dependencies {
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("org.jsoup:jsoup:1.18.1")
}

version = 1

cloudstream {
    setRepo(System.getenv("GITHUB_REPOSITORY") ?: "https://github.com/TheAlyss/cloudstream-AlyssRepo")
    description = "Nonton Anime Subtitle Indonesia dari Animasu"
    authors = listOf("TheAlyss")
    language = "id"
    status = 1
    tvTypes = listOf("Anime", "TvSeries", "Movie")
    iconUrl = "https://2.bp.blogspot.com/-QiWOTKfmgHg/XcWMAQMFtzI/AAAAAAAAFkM/9X0xgYkMVEAndahC2JNb-v4tFX8JLOWRACLcBGAsYHQ/s200/animasudotnet_simple_logo.png"
}
