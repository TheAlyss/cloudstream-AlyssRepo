android {
    namespace = "com.moviebox"
}

dependencies {
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("org.jsoup:jsoup:1.18.1")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.18.2")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.18.2")
}

version = 9

cloudstream {
    setRepo(System.getenv("GITHUB_REPOSITORY") ?: "https://github.com/TheAlyss/cloudstream-AlyssRepo")
    description = "MovieBox - Streaming Movie Subtitle Indonesia"
    authors = listOf("TheAlyss")
    language = "id"
    status = 1
    tvTypes = listOf(
        "TvSeries",
        "Movie",
        "AsianDrama",
    )
    iconUrl = "https://movieboxph.app/wp-content/uploads/2025/11/Movie-Box-icon.webp"
}
