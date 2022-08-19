package com.freya02.botcommands.api.commands.annotations;

import com.freya02.botcommands.api.commands.application.ApplicationCommand;
import com.freya02.botcommands.api.commands.application.CommandPath;
import com.freya02.botcommands.api.commands.application.slash.ApplicationGeneratedValueSupplier;
import com.freya02.botcommands.api.commands.prefixed.TextCommand;
import com.freya02.botcommands.api.commands.prefixed.TextGeneratedValueSupplier;
import com.freya02.botcommands.api.parameters.ParameterType;
import net.dv8tion.jda.api.entities.Guild;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a parameter as being a generated option
 *
 * <p>
 * <b>For text commands:</b>
 * <br>You will have to override {@link TextCommand#getGeneratedValueSupplier(CommandPath, String, ParameterType)}
 * and return, on the correct command path/option name, an appropriate {@link TextGeneratedValueSupplier} that will generate an object of the correct type.
 *
 * <p>
 * <b>For application commands:</b>
 * <br>You will have to override {@link ApplicationCommand#getGeneratedValueSupplier(Guild, String, CommandPath, String, ParameterType)}
 * and return, on the correct guild/command id/command path/option name, an appropriate {@link ApplicationGeneratedValueSupplier} that will generate an object of the correct type.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.PARAMETER})
public @interface GeneratedOption {
}