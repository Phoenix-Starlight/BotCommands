package io.github.freya022.botcommands.internal.parameters.resolvers

import io.github.freya022.botcommands.api.core.service.annotations.Resolver
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.User

@Resolver
internal class UserResolver internal constructor(): AbstractUserSnowflakeResolver<UserResolver, User>(User::class) {
    override fun transformEntities(user: User, member: Member?): User = user
}