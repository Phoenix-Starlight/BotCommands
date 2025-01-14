package io.github.freya022.wiki.switches

import io.github.freya022.botcommands.api.core.service.CustomConditionChecker
import io.github.freya022.botcommands.api.core.service.ServiceContainer
import io.github.freya022.botcommands.api.core.service.annotations.Condition
import io.github.freya022.botcommands.api.core.service.getService
import io.github.freya022.wiki.config.Config

// --8<-- [start:dev_command_annotated_condition-annotation-kotlin]
// Same targets as service annotations
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY_GETTER)
// The implementation of our CustomConditionChecker
@Condition(type = DevCommandChecker::class)
annotation class DevCommand
// --8<-- [end:dev_command_annotated_condition-annotation-kotlin]

// --8<-- [start:dev_command_annotated_condition-checker-kotlin]
// Checks services annotated with @DevCommand
object DevCommandChecker : CustomConditionChecker<DevCommand> {
    override val annotationType: Class<DevCommand> = DevCommand::class.java

    override fun checkServiceAvailability(serviceContainer: ServiceContainer, checkedClass: Class<*>, annotation: DevCommand): String? {
        val config = serviceContainer.getService<Config>() // Suppose this is your configuration
        if (!config.enableDevMode) {
            return "Dev mode is disable in the configuration" // Do not allow the dev commands!
        }
        return null // No error message, allow the tag command!
    }
}
// --8<-- [end:dev_command_annotated_condition-checker-kotlin]