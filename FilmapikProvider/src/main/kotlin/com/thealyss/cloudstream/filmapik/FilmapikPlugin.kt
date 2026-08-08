package com.thealyss.cloudstream.filmapik

import android.content.Context
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class FilmapikPlugin : Plugin() {
    override fun load(context: Context) {
        registerMainAPI(FilmapikProvider())
        registerExtractorAPI(Byseqekaho())
        registerExtractorAPI(AbyssPlayer())
        registerExtractorAPI(VipServer())
    }
}
