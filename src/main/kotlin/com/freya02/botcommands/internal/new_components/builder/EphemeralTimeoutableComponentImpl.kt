package com.freya02.botcommands.internal.new_components.builder

import com.freya02.botcommands.api.new_components.builder.IEphemeralTimeoutableComponent
import com.freya02.botcommands.internal.new_components.new.EphemeralTimeout
import kotlinx.datetime.Clock
import kotlin.time.Duration

class EphemeralTimeoutableComponentImpl : IEphemeralTimeoutableComponent<EphemeralTimeoutableComponentImpl> {
    override var timeout: EphemeralTimeout? = null
        private set

    @JvmSynthetic
    override fun timeout(timeout: Duration, handler: suspend () -> Unit): EphemeralTimeoutableComponentImpl = this.also {
        this.timeout = EphemeralTimeout(Clock.System.now() + timeout, handler)
    }
}