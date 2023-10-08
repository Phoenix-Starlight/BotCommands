package io.github.freya022.botcommands.api.core.service.annotations

import io.github.freya022.botcommands.api.core.service.ServiceContainer

/**
 * No-op annotation marking a class as an injected service.
 *
 * The service needs to be instantiated and registered manually via [ServiceContainer.putService].
 *
 * This may be good for situations where services are defined by strategies (see the Strategy design pattern),
 * an example would be other services could depend on the interface marked as an InjectedService.
 *
 * If a class has a [ServiceType] of the injected service's interface then this interface can be requested.
 *
 * @see BService @BService
 */
@MustBeDocumented
@Target(AnnotationTarget.CLASS)
annotation class InjectedService(val message: String = "This service does not exist yet, it may be created under certain conditions")