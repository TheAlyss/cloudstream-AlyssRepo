android {
    namespace = "com.thealyss.cloudstream.ylnime"
}

dependencies {
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("org.jsoup:jsoup:1.18.1")
}

version = 6

cloudstream {
    setRepo(System.getenv("GITHUB_REPOSITORY") ?: "https://github.com/TheAlyss/cloudstream-AlyssRepo")
    description = "Nonton Anime Subtitle Indonesia dari YLnime"
    authors = listOf("TheAlyss")
    language = "id"
    status = 1
    tvTypes = listOf("Anime", "TvSeries", "Movie")
    iconUrl = "https://ylnime.com/banner.jpg"
}
