package io.github.freya022.botcommands.api.commands.prefixed;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@FunctionalInterface
public interface TextGeneratedValueSupplier {
	@Nullable
	Object getDefaultValue(@NotNull BaseCommandEvent event);
}