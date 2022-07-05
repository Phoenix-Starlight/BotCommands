package com.freya02.botcommands.api.application.builder

import com.freya02.botcommands.api.application.AutocompleteCacheInfo
import com.freya02.botcommands.api.application.slash.autocomplete.AutocompletionCacheMode

class AutocompleteCacheInfoBuilder internal constructor() {
    fun build() = AutocompleteCacheInfo(this)

    var cacheMode: AutocompletionCacheMode = AutocompletionCacheMode.NO_CACHE
    var cacheSize: Long = 2048
    var guildLocal: Boolean = false
    var userLocal: Boolean = false
    var channelLocal: Boolean = false
}