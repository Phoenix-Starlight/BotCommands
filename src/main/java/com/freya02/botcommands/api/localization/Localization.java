package com.freya02.botcommands.api.localization;

import com.freya02.botcommands.api.Logging;
import com.freya02.botcommands.api.localization.providers.LocalizationBundleProviders;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.io.IOException;
import java.util.*;

//TODO docs
//TODO LocalizationTemplate should be an interface with the #localize(Entry...) method
// LocalizationBundleProvider which, for a given bundle name & locale, gives an object containing a map of String -> LT and different attributes like inheritance
// LocalizationBundleProvider should also control possible inheritance, not this class
// Providers are cycled through until an object is given
//Low level API
public class Localization {
	private static final Logger LOGGER = Logging.getLogger();
	private static final ResourceBundle.Control CONTROL = ResourceBundle.Control.getNoFallbackControl(ResourceBundle.Control.FORMAT_DEFAULT);
	private static final Map<LocalizationKey, Localization> localizationMap = new HashMap<>();

	private final Map<String, ? extends LocalizationTemplate> strings;
	private final Locale effectiveLocale;

	private Localization(@NotNull LocalizationBundle bundle) {
		this.effectiveLocale = bundle.getEffectiveLocale();
		this.strings = bundle.getTemplateMap();
	}

	@Nullable
	private static BestLocale chooseBestLocale(String baseName, Locale targetLocale) throws IOException {
		final List<Locale> candidateLocales = CONTROL.getCandidateLocales(baseName, targetLocale);

		for (Locale candidateLocale : candidateLocales) {
			//Try to retrieve with the locale
			final LocalizationBundle localizationBundle = LocalizationBundleProviders.cycleProviders(baseName, candidateLocale);

			if (localizationBundle != null) {
				if (!localizationBundle.getEffectiveLocale().equals(candidateLocale)) {
					throw new IllegalArgumentException("LocalizationBundle locale '%s' differ from requested locale '%s'".formatted(localizationBundle.getEffectiveLocale(), candidateLocale));
				}

				return new BestLocale(localizationBundle.getEffectiveLocale(), localizationBundle);
			}
		}

		return null;
	}

	@Nullable
	private static Localization retrieveBundle(String baseName, Locale targetLocale) throws IOException {
		final BestLocale bestLocale = chooseBestLocale(baseName, targetLocale);

		if (bestLocale == null) {
			LOGGER.warn("Could not find localization resources for '{}'", baseName);

			return null;
		} else {
			if (!bestLocale.locale().equals(targetLocale)) { //Not default
				if (bestLocale.locale().toString().isEmpty()) { //neutral lang
					LOGGER.warn("Unable to find bundle '{}' with locale '{}', falling back to neutral lang", baseName, targetLocale);
				} else {
					LOGGER.warn("Unable to find bundle '{}' with locale '{}', falling back to '{}'", baseName, targetLocale, bestLocale.locale());
				}
			}

			return new Localization(bestLocale.bundle());
		}
	}

	@Nullable
	public static synchronized Localization getInstance(@NotNull String bundleName, @NotNull Locale locale) {
		final LocalizationKey key = new LocalizationKey(bundleName, locale);
		final Localization value = localizationMap.get(key);

		if (value != null) {
			return value;
		} else {
			try {
				final Localization newValue = retrieveBundle(bundleName, locale);
				localizationMap.put(key, newValue);

				return newValue;
			} catch (Exception e) {
				throw new RuntimeException("Unable to get bundle '%s' for locale '%s'".formatted(bundleName, locale), e);
			}
		}
	}

	@Nullable
	public LocalizationTemplate get(String path) {
		return strings.get(path);
	}

	public Locale getEffectiveLocale() {
		return effectiveLocale;
	}

	private record LocalizationKey(String bundleName, Locale locale) {}

	private record BestLocale(Locale locale, LocalizationBundle bundle) {}

	public record Entry(String key, Object value) {
		/**
		 * Create a new localization entry, this binds a key (from a templated string) into a value
		 * <b>Highly recommended to use this method with a static import</b>
		 *
		 * @param key   The key from the templated string
		 * @param value The value to assign it to
		 * @return The entry
		 */
		@NotNull
		public static Entry entry(@NotNull String key, @NotNull Object value) {
			return new Entry(key, value);
		}
	}
}
