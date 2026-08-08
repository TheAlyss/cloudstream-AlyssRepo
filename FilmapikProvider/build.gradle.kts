android {
    namespace = "com.thealyss.cloudstream.filmapik"
}

dependencies {
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("org.jsoup:jsoup:1.18.1")
}

version = 4

cloudstream {
    setRepo(System.getenv("GITHUB_REPOSITORY") ?: "https://github.com/TheAlyss/cloudstream-AlyssRepo")
    description = "Nonton Film & Series Subtitle Indonesia dari Filmapik"
    authors = listOf("TheAlyss")
    status = 1
    tvTypes = listOf("Movie", "TvSeries", "AsianDrama", "Anime")
    iconUrl = "https://raw.githubusercontent.com/TheAlyss/cloudstream-AlyssRepo/builds/icon.png"
}
