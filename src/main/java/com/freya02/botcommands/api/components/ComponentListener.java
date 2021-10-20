package com.freya02.botcommands.api.components;

import com.freya02.botcommands.api.BContext;
import com.freya02.botcommands.api.components.event.ButtonEvent;
import com.freya02.botcommands.api.components.event.SelectionEvent;
import com.freya02.botcommands.api.parameters.ComponentParameterResolver;
import com.freya02.botcommands.internal.Logging;
import com.freya02.botcommands.internal.application.CommandParameter;
import com.freya02.botcommands.internal.components.ComponentDescriptor;
import com.freya02.botcommands.internal.utils.Utils;
import net.dv8tion.jda.api.events.interaction.ButtonClickEvent;
import net.dv8tion.jda.api.events.interaction.GenericComponentInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.SelectionMenuEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

public class ComponentListener extends ListenerAdapter {
	private static final Logger LOGGER = Logging.getLogger();

	private final ExecutorService idHandlingExecutor = Executors.newSingleThreadExecutor(r -> {
		final Thread thread = new Thread(r);
		thread.setDaemon(false);
		thread.setUncaughtExceptionHandler((t, e) -> Utils.printExceptionString("An unexpected exception happened in a component ID handler thread '" + t.getName() + "':", e));
		thread.setName("Component ID handling thread");

		return thread;
	});

	private final BContext context;
	private final ComponentManager componentManager;

	private final Map<String, ComponentDescriptor> buttonsMap;
	private final Map<String, ComponentDescriptor> selectionMenuMap;

	private int callbackThreadNumber;
	private final ExecutorService callbackExecutor = Utils.createCommandPool(r -> {
		final Thread thread = new Thread(r);
		thread.setDaemon(false);
		thread.setUncaughtExceptionHandler((t, e) -> Utils.printExceptionString("An unexpected exception happened in a component callback thread '" + t.getName() + "':", e));
		thread.setName("Component callback thread #" + callbackThreadNumber++);

		return thread;
	});

	public ComponentListener(BContext context, Map<String, ComponentDescriptor> buttonsMap, Map<String, ComponentDescriptor> selectionMenuMap) {
		this.context = context;
		this.componentManager = Utils.getComponentManager(context);
		this.buttonsMap = buttonsMap;
		this.selectionMenuMap = selectionMenuMap;

		Components.setContext(context);
	}

	@Override
	public void onGenericComponentInteractionCreate(@NotNull GenericComponentInteractionCreateEvent event) {
		if (!(event instanceof ButtonClickEvent) && !(event instanceof SelectionMenuEvent)) return;

		idHandlingExecutor.submit(() -> handleComponentInteraction(event));
	}

	private void handleComponentInteraction(@NotNull GenericComponentInteractionCreateEvent event) {
		final ComponentType idType = componentManager.getIdType(event.getComponentId());

		if (idType == null) {
			event.reply(context.getDefaultMessages(event.getGuild()).getNullComponentTypeErrorMsg())
					.setEphemeral(true)
					.queue();

			return;
		}

		if ((idType == ComponentType.PERSISTENT_BUTTON || idType == ComponentType.LAMBDA_BUTTON) && !(event instanceof ButtonClickEvent)) {
			LOGGER.error("Received a button id type but event is not a ButtonClickEvent");

			return;
		}

		if ((idType == ComponentType.PERSISTENT_SELECTION_MENU || idType == ComponentType.LAMBDA_SELECTION_MENU) && !(event instanceof SelectionMenuEvent)) {
			LOGGER.error("Received a selection menu id type but event is not a SelectionMenuEvent");

			return;
		}

		switch (idType) {
			case PERSISTENT_BUTTON -> componentManager.handlePersistentButton(event,
					e -> onError(event, e.getReason()),
					data -> callbackExecutor.submit(() -> handlePersistentComponent(event,
							buttonsMap,
							data.getHandlerName(),
							data.getArgs(),
							() -> new ButtonEvent(context, (ButtonClickEvent) event))));
			case LAMBDA_BUTTON -> componentManager.handleLambdaButton(event,
					e -> onError(event, e.getReason()),
					data -> callbackExecutor.submit(() -> data.getConsumer().accept(new ButtonEvent(context, (ButtonClickEvent) event)))
			);
			case PERSISTENT_SELECTION_MENU -> componentManager.handlePersistentSelectionMenu(event,
					e -> onError(event, e.getReason()),
					data -> callbackExecutor.submit(() -> handlePersistentComponent(event,
							selectionMenuMap,
							data.getHandlerName(),
							data.getArgs(),
							() -> new SelectionEvent(context, (SelectionMenuEvent) event))));
			case LAMBDA_SELECTION_MENU -> componentManager.handleLambdaSelectionMenu(event,
					e -> onError(event, e.getReason()),
					data -> callbackExecutor.submit(() -> data.getConsumer().accept(new SelectionEvent(context, (SelectionMenuEvent) event))));
			default -> throw new IllegalArgumentException("Unknown id type: " + idType.name());
		}
	}

	private void handlePersistentComponent(GenericComponentInteractionCreateEvent event,
	                                       Map<String, ComponentDescriptor> map,
	                                       String handlerName,
	                                       String[] args,
	                                       Supplier<? extends GenericComponentInteractionCreateEvent> eventFunction) {
		final ComponentDescriptor descriptor = map.get(handlerName);

		if (descriptor == null) {
			LOGGER.error("No component descriptor found for component handler '{}'", handlerName);

			return;
		}

		final var parameters = descriptor.getParameters();
		if (parameters.getOptionCount() != args.length) {
			LOGGER.warn("Resolver for {} has {} arguments but component had {} data objects", Utils.formatMethodShort(descriptor.getMethod()), parameters.size(), args);

			onError(event, "Invalid component data");

			return;
		}

		try {
			//For some reason using an array list instead of a regular array
			// magically unboxes primitives when passed to Method#invoke
			final List<Object> methodArgs = new ArrayList<>(parameters.size() + 1);

			methodArgs.add(eventFunction.get());

			int optionIndex = 0;
			for (final CommandParameter<ComponentParameterResolver> parameter : parameters) {
				final Object obj;
				if (parameter.isOption()) {
					final String arg = args[optionIndex];
					optionIndex++;

					obj = parameter.getResolver().resolve(event, arg);

					if (obj == null) {
						LOGGER.warn("Component id '{}', tried to resolve '{}' with an option resolver {} on method {} but result is null",
								event.getComponentId(),
								arg,
								parameter.getCustomResolver().getClass().getSimpleName(),
								Utils.formatMethodShort(descriptor.getMethod()));

						return;
					}
				} else {
					obj = parameter.getCustomResolver().resolve(event);

					if (obj == null) {
						LOGGER.warn("Component id '{}', tried to use custom resolver {} on method {} but result is null",
								event.getComponentId(),
								parameter.getCustomResolver().getClass().getSimpleName(),
								Utils.formatMethodShort(descriptor.getMethod()));

						return;
					}
				}

				methodArgs.add(obj);
			}

			descriptor.getMethod().invoke(descriptor.getInstance(), methodArgs.toArray());
		} catch (Exception e) {
			LOGGER.error("An exception occurred while handling a persistent component '{}' with args {}", handlerName, Arrays.toString(args), e);
		}
	}

	private void onError(GenericComponentInteractionCreateEvent event, String reason) {
		if (reason != null) {
			event.reply(reason)
					.setEphemeral(true)
					.queue();
		} else {
			event.deferEdit().queue();
		}
	}
}