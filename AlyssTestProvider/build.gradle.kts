android {
    namespace = "com.thealyss.cloudstream"
}

dependencies {
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
}

version = 1

cloudstream {
    description = "Test Provider for Alyss CloudStream Repository"
    authors = listOf("TheAlyss")
    status = 1
    tvTypes = listOf("Movie", "TvSeries")
    iconUrl = "https://raw.githubusercontent.com/TheAlyss/cloudstream-AlyssRepo/builds/icon.png"
}
