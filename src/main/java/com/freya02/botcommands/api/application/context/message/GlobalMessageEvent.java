package com.freya02.botcommands.api.application.context.message;

import com.freya02.botcommands.api.BContext;
import com.freya02.botcommands.api.localization.GuildLocalizable;
import com.freya02.botcommands.api.localization.Localizable;
import com.freya02.botcommands.api.localization.Localization;
import com.freya02.botcommands.api.localization.UserLocalizable;
import com.freya02.botcommands.internal.BContextImpl;
import com.freya02.botcommands.internal.localization.EventLocalizer;
import kotlin.reflect.KFunction;
import net.dv8tion.jda.api.events.interaction.command.MessageContextInteractionEvent;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;

public class GlobalMessageEvent extends MessageContextInteractionEvent implements GuildLocalizable, UserLocalizable, Localizable {
	private final EventLocalizer localizer;

	private final BContext context;

	public GlobalMessageEvent(@NotNull KFunction<?> function, BContextImpl context, MessageContextInteractionEvent event) {
		super(event.getJDA(), event.getResponseNumber(), event.getInteraction());

		this.context = context;
		this.localizer = new EventLocalizer(context, function, isFromGuild() ? event.getGuildLocale() : null, event.getUserLocale());
	}

	@NotNull
	public BContext getContext() {
		return context;
	}

	@Override
	@NotNull
	public String localizeGuild(@NotNull String localizationBundle, @NotNull String localizationPath, @NotNull Localization.Entry @NotNull ... entries) {return localizer.localizeGuild(localizationBundle, localizationPath, entries);}

	@Override
	@NotNull
	public String localizeGuild(@NotNull String localizationPath, @NotNull Localization.Entry @NotNull ... entries) {return localizer.localizeGuild(localizationPath, entries);}

	@Override
	@NotNull
	public String localizeUser(@NotNull String localizationBundle, @NotNull String localizationPath, @NotNull Localization.Entry @NotNull ... entries) {return localizer.localizeUser(localizationBundle, localizationPath, entries);}

	@Override
	@NotNull
	public String localizeUser(@NotNull String localizationPath, @NotNull Localization.Entry @NotNull ... entries) {return localizer.localizeUser(localizationPath, entries);}

	@Override
	@NotNull
	public String localize(@NotNull Locale locale, @NotNull String localizationBundle, @NotNull String localizationPath, @NotNull Localization.Entry @NotNull ... entries) {return localizer.localize(locale, localizationBundle, localizationPath, entries);}

	@Override
	@NotNull
	public String localize(@NotNull String localizationBundle, @NotNull String localizationPath, @NotNull Localization.Entry @NotNull ... entries) {return localizer.localize(localizationBundle, localizationPath, entries);}

	@Override
	@NotNull
	public String localize(@NotNull Locale locale, @NotNull String localizationPath, @NotNull Localization.Entry @NotNull ... entries) {return localizer.localize(locale, localizationPath, entries);}

	@Override
	@NotNull
	public String localize(@NotNull String localizationPath, @NotNull Localization.Entry @NotNull ... entries) {return localizer.localize(localizationPath, entries);}

	@Override
	@NotNull
	public String getLocalizationBundle() {return localizer.getLocalizationBundle();}
}
