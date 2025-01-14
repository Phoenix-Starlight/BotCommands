package io.github.freya022.botcommands.internal.utils

import dev.minn.jda.ktx.coroutines.await
import io.github.freya022.botcommands.api.core.utils.deleteDelayed
import io.github.freya022.botcommands.api.core.utils.runIgnoringResponse
import io.github.freya022.botcommands.internal.core.exceptions.InternalException
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import net.dv8tion.jda.api.interactions.callbacks.IReplyCallback
import net.dv8tion.jda.api.requests.ErrorResponse
import java.lang.reflect.InvocationTargetException
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract
import kotlin.reflect.KFunction
import kotlin.time.Duration.Companion.seconds

internal fun throwInternal(message: String): Nothing =
    throw InternalException(message)

internal fun throwInternal(function: KFunction<*>, message: String): Nothing =
    throw InternalException("${function.shortSignature} : $message")

internal fun throwUser(function: KFunction<*>, message: String): Nothing =
    throw IllegalArgumentException("${function.shortSignature} : $message")

internal fun rethrowUser(function: KFunction<*>, message: String, e: Throwable): Nothing =
    throw RuntimeException("${function.shortSignature} : $message", e)

internal fun rethrowUser(message: String, e: Throwable): Nothing =
    throw RuntimeException(message, e)

internal fun throwUser(message: String): Nothing =
    throw IllegalArgumentException(message)

@OptIn(ExperimentalContracts::class)
internal inline fun requireUser(value: Boolean, function: KFunction<*>, lazyMessage: () -> String) {
    contract {
        returns() implies value
    }

    if (!value) {
        throwUser(function, lazyMessage())
    }
}

@OptIn(ExperimentalContracts::class)
internal inline fun requireUser(value: Boolean, lazyMessage: () -> String) {
    contract {
        returns() implies value
    }

    if (!value) {
        throwUser(lazyMessage())
    }
}

internal fun Throwable.unwrap(): Throwable {
    if (this is InvocationTargetException) return targetException
    return this
}

internal suspend fun IReplyCallback.replyExceptionMessage(
    message: String
) = runIgnoringResponse(ErrorResponse.UNKNOWN_INTERACTION, ErrorResponse.UNKNOWN_WEBHOOK) {
    if (isAcknowledged) {
        // Give ourselves 5 seconds to delete
        if (Instant.fromEpochMilliseconds(hook.expirationTimestamp) - 5.seconds > Clock.System.now())
            hook.sendMessage(message)
                .setEphemeral(true)
                .deleteDelayed(5.seconds)
                .await()
    } else {
        reply(message)
            .setEphemeral(true)
            .await()
    }
}