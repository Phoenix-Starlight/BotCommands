package com.freya02.botcommands.api.components.annotations;

import com.freya02.botcommands.api.commands.application.ApplicationCommand;
import com.freya02.botcommands.api.commands.prefixed.TextCommand;
import com.freya02.botcommands.api.components.event.EntitySelectEvent;
import com.freya02.botcommands.api.components.event.StringSelectEvent;
import com.freya02.botcommands.api.parameters.ParameterResolver;
import net.dv8tion.jda.api.interactions.components.selections.EntitySelectMenu.SelectTarget;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation for defining a selection menu listener,
 * this has to be the same name as the one given to {@link Components#stringSelectionMenu(String, Object...)} or {@link Components#entitySelectionMenu(SelectTarget, String, Object...)}
 *
 * <p>
 *
 * Requirements:
 * <ul>
 *     <li><b>Selection menu listeners can only be put on methods that are inside a class that extends {@link TextCommand} or {@link ApplicationCommand}</b></li>
 *     <li><b>These handlers also need to have a {@link StringSelectEvent} or {@link EntitySelectEvent} as their first argument</b></li>
 * </ul>
 *
 * <p>
 * <i>Supported parameters in {@link ParameterResolver}</i>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface JDASelectionMenuListener {
	/**
	 * Name of the selection menu listener, this is used to find back the handler method after a button has been clicked
	 *
	 * @return Name of the selection menu listener
	 */
	String name();
}