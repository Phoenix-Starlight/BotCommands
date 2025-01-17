package io.github.freya022.botcommands.api.core;

import io.github.freya022.botcommands.api.core.config.BServiceConfigBuilder;
import io.github.freya022.botcommands.api.core.service.annotations.BService;
import io.github.freya022.botcommands.api.core.service.annotations.InterfacedService;
import net.dv8tion.jda.api.events.Event;
import net.dv8tion.jda.api.interactions.Interaction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Interface with a method called everytime the framework catches an uncaught exception.
 * <br>This remplaces the standard implementation which logs the error and optionally sends it to the bot owners
 *
 * <p>
 * <b>Notes:</b> You will need to handle things such as already acknowledged interactions (in the case of interaction events,
 * where the exception happened after the interaction has been acknowledged), see {@link Interaction#isAcknowledged()}
 *
 * <p>
 * <b>Usage</b>: Register your instance as a service with {@link BService}
 * or {@link BServiceConfigBuilder#getServiceAnnotations() any annotation that enables your class for dependency injection}.
 *
 * <p>
 * You are still free from extending {@link GlobalExceptionHandlerAdapter}.
 *
 * @see GlobalExceptionHandlerAdapter
 * @see InterfacedService @InterfacedService
 */
@InterfacedService(acceptMultiple = false)
public interface GlobalExceptionHandler {
	/**
	 * <b>Note: You are sent a generic Event, you will need to check it against SlashCommandInteractionEvent, GuildMessageReceivedEvent, etc... in order to differentiate events</b>
	 *
	 * @param event     The event which triggered this exception
	 * @param throwable The throwable which got threw
	 */
	void onException(@Nullable Event event, @NotNull Throwable throwable);
}
