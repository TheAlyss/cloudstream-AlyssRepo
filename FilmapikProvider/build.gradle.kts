android {
    namespace = "com.thealyss.cloudstream.filmapik"
}

dependencies {
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("org.jsoup:jsoup:1.18.1")
}

version = 13

cloudstream {
    setRepo(System.getenv("GITHUB_REPOSITORY") ?: "https://github.com/TheAlyss/cloudstream-AlyssRepo")
    description = "Nonton Film & Series Subtitle Indonesia dari Filmapik"
    authors = listOf("TheAlyss")
    language = "id"
    status = 1
    tvTypes = listOf("Movie", "TvSeries", "AsianDrama", "Anime")
    iconUrl = "https://filmapik.dev/wp-content/uploads/2026/06/cropped-apple-icon-180x180-1-1-270x270.webp"
}
