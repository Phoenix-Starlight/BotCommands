package com.freya02.botcommands.internal.application.slash.autocomplete.caches;

import com.freya02.botcommands.internal.RunnableEx;
import net.dv8tion.jda.api.interactions.commands.Command;

import java.util.List;
import java.util.function.Consumer;

public class NoCacheAutocompletion extends AbstractAutocompletionCache {
	@Override
	public void retrieveAndCall(String stringOption, Consumer<List<Command.Choice>> choiceCallback, RunnableEx valueComputer) throws Exception {
		valueComputer.run(); //Always compute the value, the result gets replied by the computer
	}

	@Override
	public void put(String stringOption, List<Command.Choice> choices) {
		//Don't cache
	}

	@Override
	public void invalidate() {
		//No cache to invalidate
	}
}
