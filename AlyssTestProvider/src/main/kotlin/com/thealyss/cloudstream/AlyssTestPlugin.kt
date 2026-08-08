package com.thealyss.cloudstream

import android.content.Context
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class AlyssTestPlugin : Plugin() {
    override fun load(context: Context) {
        // Register the main API test provider
        registerMainAPI(AlyssTestProvider())
    }
}
