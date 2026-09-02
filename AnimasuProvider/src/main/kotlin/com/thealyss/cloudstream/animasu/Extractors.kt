package com.thealyss.cloudstream.animasu

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*

open class CustomVidHide : ExtractorApi() {
    override var name = "VidHide"
    override var mainUrl = "https://vidhidepro.com"
    override val requiresReferer = false

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        try {
            val res = app.get(url, referer = referer ?: "https://animasu.love/").text
            val unpacked = getAndUnpack(res) ?: res
            val m3u8Match = Regex("""file\s*:\s*["']([^"']+\.m3u8[^"']*)["']""").find(unpacked)
                ?: Regex("""["']?sources["']?\s*:\s*\[\s*\{[^}]*file\s*:\s*["']([^"']+)["']""").find(unpacked)
            if (m3u8Match != null) {
                val m3u8Url = m3u8Match.groupValues[1]
                M3u8Helper.generateM3u8(
                    name,
                    m3u8Url,
                    url
                ).forEach(callback)
            }
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }
}

class VidHideFast : CustomVidHide() {
    override var mainUrl = "https://vidhidefast.com"
}

class VidHidePro : CustomVidHide() {
    override var mainUrl = "https://vidhidepro.com"
}

class VidHideVip : CustomVidHide() {
    override var mainUrl = "https://vidhidevip.com"
}

class VidHidePre : CustomVidHide() {
    override var mainUrl = "https://vidhidepre.com"
}

class Callistanise : CustomVidHide() {
    override var mainUrl = "https://callistanise.com"
}
