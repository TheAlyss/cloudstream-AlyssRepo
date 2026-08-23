package com.thealyss.cloudstream.filmapik

import android.content.Context
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class FilmapikPlugin : Plugin() {
    override fun load(context: Context) {
        registerMainAPI(FilmapikProvider())

        // Filemoon Mirrors
        registerExtractorAPI(Byseqekaho())
        registerExtractorAPI(FurherIn())
        registerExtractorAPI(MoonPlayerIn())
        registerExtractorAPI(KerapoxyCc())

        // VIP Server / EfekStream Mirrors
        registerExtractorAPI(VipServer())
        registerExtractorAPI(FaEfekStream())
        registerExtractorAPI(EfekStreamV3())
        registerExtractorAPI(EfekStreamV4())
        registerExtractorAPI(EfekStreamMain())

        // StreamWish Mirrors
        registerExtractorAPI(AwishPro())
        registerExtractorAPI(MwishPro())
        registerExtractorAPI(FlaswishCom())
        registerExtractorAPI(WishembedPro())
        registerExtractorAPI(EmbedwishCom())
        registerExtractorAPI(SfastwishCom())

        // DoodStream Mirrors
        registerExtractorAPI(D000dCom())
        registerExtractorAPI(D0000dCom())
        registerExtractorAPI(Ds2playCom())

        // FileLions Mirrors
        registerExtractorAPI(FilelionsOnline())
        registerExtractorAPI(FilelionsSite())
        registerExtractorAPI(LionplayNet())
        registerExtractorAPI(FdewsdcOrg())

        // Abyss / Hydrax
        registerExtractorAPI(AbyssPlayer())
    }
}
