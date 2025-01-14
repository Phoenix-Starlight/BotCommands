package io.github.freya022.botcommands.api.core.service;

import io.github.freya022.botcommands.api.core.BContext;
import io.github.freya022.botcommands.api.core.config.BServiceConfigBuilder;
import org.jetbrains.annotations.NotNull;

/**
 * Functional interface where you supply an instance of the given class type (your command)
 *
 * @param <T> Type of the class to instantiate
 *
 * @see BServiceConfigBuilder#registerInstanceSupplier(Class, InstanceSupplier)
 */
public interface InstanceSupplier<T> {
    @NotNull
    T supply(@NotNull BContext context);
}
