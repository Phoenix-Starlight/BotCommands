package io.github.freya022.wiki.java.resolvers;

import io.github.freya022.botcommands.api.core.service.annotations.Resolver;
import io.github.freya022.botcommands.api.core.service.annotations.ServiceName;
import io.github.freya022.botcommands.api.parameters.ParameterResolver;
import io.github.freya022.botcommands.api.parameters.Resolvers;
import io.github.freya022.wiki.switches.wiki.WikiDetailProfile;
import io.github.freya022.wiki.switches.wiki.WikiLanguage;

import java.util.concurrent.TimeUnit;

public class TimeUnitResolverSimplified {
    @ServiceName("timeUnitResolverSimplifiedJava")
    @WikiLanguage(WikiLanguage.Language.JAVA)
    @WikiDetailProfile(WikiDetailProfile.Profile.SIMPLIFIED)
    // --8<-- [start:time_unit_resolver-simplified-java]
    @Resolver
    public static ParameterResolver<?, TimeUnit> getTimeUnitResolverSimplified() {
        // The displayed name should be lowercase with the first letter uppercase, see Resolvers#toHumanName
        return Resolvers.enumResolver(TimeUnit.class, new TimeUnit[]{TimeUnit.SECONDS, TimeUnit.MINUTES, TimeUnit.HOURS, TimeUnit.DAYS});
    }
}
// --8<-- [end:time_unit_resolver-simplified-java]