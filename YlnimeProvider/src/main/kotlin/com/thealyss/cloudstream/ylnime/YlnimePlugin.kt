package com.thealyss.cloudstream.ylnime

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class YlnimePlugin: Plugin() {
    override fun load(context: Context) {
        registerMainAPI(YlnimeProvider())
    }
}
