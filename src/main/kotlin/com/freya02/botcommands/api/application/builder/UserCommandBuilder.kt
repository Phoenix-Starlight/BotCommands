package com.freya02.botcommands.api.application.builder

import com.freya02.botcommands.api.application.CommandPath
import com.freya02.botcommands.internal.BContextImpl
import com.freya02.botcommands.internal.application.context.user.UserCommandInfo

class UserCommandBuilder internal constructor(private val context: BContextImpl, path: CommandPath) :
    ApplicationCommandBuilder(path) {

    internal fun build(): UserCommandInfo {
        return UserCommandInfo(context, this)
    }
}