package com.freya02.botcommands.internal.application.context.user

import com.freya02.botcommands.api.parameters.UserContextParameterResolver
import com.freya02.botcommands.internal.application.context.ContextCommandParameter
import kotlin.reflect.KParameter

class UserContextCommandParameter(
    parameter: KParameter,
    resolver: UserContextParameterResolver
) : ContextCommandParameter<UserContextParameterResolver>(parameter, resolver)